/*
 * Imperium, the software collection powering the Chaotic Neutral network.
 * Copyright (C) 2024  Xpdustry
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.xpdustry.imperium.mindustry.security

import arc.Events
import arc.util.Time
import arc.util.io.Writes
import com.google.common.net.InetAddresses
import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.MindustryConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.containsLink
import com.xpdustry.imperium.common.misc.logger
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.common.network.VpnDetection
import com.xpdustry.imperium.common.security.AddressWhitelist
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.kick
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.time.Duration
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.core.Version
import mindustry.game.EventType.ConnectPacketEvent
import mindustry.game.EventType.PlayerConnect
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.net.Administration.PlayerInfo
import mindustry.net.NetConnection
import mindustry.net.Packets
import mindustry.net.Packets.KickReason
import okhttp3.OkHttpClient

private val logger = logger("ROOT")

class GatekeeperListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val pipeline = instances.get<GatekeeperPipeline>()
    private val vpn = instances.get<VpnDetection>()
    private val punishments = instances.get<PunishmentManager>()
    private val http = instances.get<OkHttpClient>()
    private val config = instances.get<MindustryConfig>()
    private val renderer = instances.get<TimeRenderer>()
    private val whitelist = instances.get<AddressWhitelist>()

    override fun onImperiumInit() {
        if (!config.security.gatekeeper) {
            logger.warn("Gatekeeper is disabled. ONLY DO IT IN DEVELOPMENT.")
        }

        pipeline.register("ddos", Priority.HIGH, DdosGatekeeper(http, config.security))
        pipeline.register(
            "punishment", Priority.NORMAL, PunishmentGatekeeper(punishments, renderer))

        pipeline.register("cracked-client", Priority.NORMAL, NameGatekeeper())
        pipeline.register("links", Priority.NORMAL) { context ->
            if (context.name.containsLink()) {
                GatekeeperResult.Failure("Your name cannot contain a link.")
            } else {
                GatekeeperResult.Success
            }
        }

        pipeline.register("vpn", Priority.LOW, VpnGatekeeper(vpn, whitelist))

        Vars.net.handleServer(Packets.ConnectPacket::class.java) { con, packet ->
            interceptPlayerConnection(con, packet, pipeline, config)
        }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}

private fun interceptPlayerConnection(
    con: NetConnection,
    packet: Packets.ConnectPacket,
    pipeline: GatekeeperPipeline,
    config: MindustryConfig
) {
    if (con.kicked) return

    if (con.address.startsWith("steam:")) {
        packet.uuid = con.address.substring("steam:".length)
    }

    Events.fire(ConnectPacketEvent(con, packet))

    con.connectTime = Time.millis()

    if (Vars.netServer.admins.isIPBanned(con.address) ||
        Vars.netServer.admins.isSubnetBanned(con.address) ||
        con.kicked ||
        !con.isConnected)
        return

    if (con.hasBegunConnecting) {
        con.kick(KickReason.idInUse, silent = true)
        return
    }

    // We do not want to save the data of DDOSers, so we postpone the saving of the player info
    val info =
        Vars.netServer.admins.getInfoOptional(packet.uuid)
            ?: PlayerInfo().apply { id = packet.uuid }

    con.hasBegunConnecting = true
    con.mobile = packet.mobile

    if (packet.uuid == null || packet.usid == null) {
        con.kick(KickReason.idInUse, silent = true)
        return
    }

    if (Vars.netServer.admins.isIDBanned(packet.uuid)) {
        con.kick(KickReason.banned)
        return
    }

    if (Time.millis() < Vars.netServer.admins.getKickTime(packet.uuid, con.address)) {
        con.kick(KickReason.recentKick, silent = true)
        return
    }

    // CHECK: Player limit
    if (Vars.netServer.admins.playerLimit > 0 &&
        Entities.getPlayers().size >= Vars.netServer.admins.playerLimit &&
        !Vars.netServer.admins.isAdmin(packet.uuid, packet.usid)) {
        con.kick(KickReason.playerLimit)
        return
    }

    // CHECK: Mods
    val mods = packet.mods.copy()
    val missing = Vars.mods.getIncompatibility(mods)
    if (!mods.isEmpty || !missing.isEmpty) {
        // can't easily be localized since kick reasons can't have formatted text with them
        val result = StringBuilder("[accent]Incompatible mods![]\n\n")
        if (!missing.isEmpty) {
            result.append("Missing:[lightgray]\n").append("> ").append(missing.toString("\n> "))
            result.append("[]\n")
        }
        if (!mods.isEmpty) {
            result
                .append("Unnecessary mods:[lightgray]\n")
                .append("> ")
                .append(mods.toString("\n> "))
        }
        con.kick(result.toString(), Duration.ZERO, silent = true)
        return
    }

    // CHECK: Whitelist
    if (!Vars.netServer.admins.isWhitelisted(packet.uuid, packet.usid)) {
        info.adminUsid = packet.usid
        info.lastName = packet.name
        info.id = packet.uuid
        Vars.netServer.admins.save()
        Call.infoMessage(con, "You are not whitelisted here.")
        logger.info(
            "&lcDo &lywhitelist-add {}&lc to whitelist the player &lb'{}'",
            packet.uuid,
            packet.name)
        con.kick(KickReason.whitelist, silent = true)
        return
    }

    // CHECK: Custom client
    if (packet.versionType == null ||
        (packet.version == -1 || packet.versionType != Version.type) &&
            Version.build != -1 &&
            Vars.netServer.admins.allowsCustomClients().not()) {
        con.kick(
            if (Version.type != packet.versionType) KickReason.typeMismatch
            else KickReason.customClient)
        return
    }

    // CHECK: Duplicate names
    if (Entities.getPlayers().any {
        it.name
            .stripMindustryColors()
            .trim()
            .equals(packet.name.stripMindustryColors().trim(), ignoreCase = true)
    }) {
        con.kick(KickReason.nameInUse, silent = true)
        return
    }

    // CHECK: Duplicate ids
    if (Entities.getPlayers().any { player ->
        player.uuid() == packet.uuid || player.usid() == packet.usid
    }) {
        con.uuid = packet.uuid
        con.kick(KickReason.idInUse, silent = true)
        return
    }

    // CHECK: Duplicate connections
    for (otherCon in Vars.net.connections) {
        if (otherCon !== con && packet.uuid == otherCon.uuid) {
            con.uuid = packet.uuid
            con.kick(KickReason.idInUse, silent = true)
            return
        }
    }

    packet.name = Vars.netServer.fixName(packet.name)

    // CHECK: Empty name
    if (packet.name.trim().stripMindustryColors().isBlank()) {
        con.kick(KickReason.nameEmpty, silent = true)
        return
    }

    // CHECK: Locale
    if (packet.locale == null) {
        packet.locale = "en"
    }

    // CHECK: Version
    if (packet.version != Version.build && Version.build != -1 && packet.version != -1) {
        con.kick(
            if (packet.version > Version.build) KickReason.serverOutdated
            else KickReason.clientOutdated,
            silent = true)
        return
    }

    if (packet.version == -1) {
        con.modclient = true
    }

    // To not spam the clients, we do our own verification through the pipeline, then we can safely
    // create the player
    ImperiumScope.MAIN.launch {
        val result =
            if (config.security.gatekeeper) {
                pipeline.pump(
                    GatekeeperContext(
                        packet.name,
                        packet.uuid,
                        packet.usid,
                        InetAddresses.forString(con.address)))
            } else {
                GatekeeperResult.Success
            }

        runMindustryThread {
            if (result is GatekeeperResult.Failure) {
                con.kick(result.reason, result.time, result.silent)
                return@runMindustryThread
            }

            // The postponed info is now saved
            Vars.netServer.admins.playerInfo.put(info.id, info)
            Vars.netServer.admins.updatePlayerJoined(packet.uuid, con.address, packet.name)

            val player = Player.create()
            player.admin = Vars.netServer.admins.isAdmin(packet.uuid, packet.usid)
            player.con = con
            player.con.usid = packet.usid
            player.con.uuid = packet.uuid
            player.con.mobile = packet.mobile
            player.name = packet.name
            player.locale = packet.locale
            player.color.set(packet.color).a(1f)

            // Save admin ID but don't overwrite it
            if (!player.admin && !info.admin) {
                info.adminUsid = packet.usid
            }

            // CHECK: Data validity or something...
            try {
                val output = Writes(DataOutputStream(ByteArrayOutputStream(127)))
                player.write(output)
            } catch (error: Throwable) {
                con.kick(KickReason.nameEmpty)
                logger.error("Failed to write player data", error)
                return@runMindustryThread
            }

            con.player = player

            // Playing in pvp mode automatically assigns players to teams
            player.team(Vars.netServer.assignTeam(player))

            Vars.netServer.sendWorldData(player)

            Events.fire(PlayerConnect(player))
        }
    }
}
