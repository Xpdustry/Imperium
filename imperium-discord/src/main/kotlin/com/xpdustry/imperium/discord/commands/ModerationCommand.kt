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
package com.xpdustry.imperium.discord.commands

import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentDuration
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.snowflake.Snowflake
import com.xpdustry.imperium.common.snowflake.timestamp
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.discord.command.annotation.Range
import com.xpdustry.imperium.discord.misc.Embed
import com.xpdustry.imperium.discord.misc.await
import com.xpdustry.imperium.discord.misc.identity
import kotlin.time.Duration
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction

class ModerationCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val punishments = instances.get<PunishmentManager>()
    private val users = instances.get<UserManager>()
    private val renderer = instances.get<TimeRenderer>()

    @ImperiumCommand(["punishment", "list"], Rank.MODERATOR)
    suspend fun onPunishmentListCommand(
        interaction: SlashCommandInteraction,
        player: Snowflake,
        @Range(min = "0") page: Int = 0
    ) {
        val reply = interaction.deferReply(true).await()
        val result = punishments.findAllByUser(player).drop(page * 10).take(10).toList()
        if (result.isEmpty()) {
            reply.sendMessage("No punishments found.").await()
            return
        }
        reply.sendMessageEmbeds(result.map { it.toEmbed() }).await()
    }

    @ImperiumCommand(["punishment", "info"], Rank.MODERATOR)
    suspend fun onPunishmentInfoCommand(interaction: SlashCommandInteraction, punishment: String) {
        val reply = interaction.deferReply(true).await()
        val id = punishment.toLongOrNull()
        if (id == null) {
            reply.sendMessage("Invalid id.").await()
            return
        }
        val result = punishments.findBySnowflake(id)
        if (result == null) {
            reply.sendMessage("No punishment found.").await()
            return
        }
        reply.sendMessageEmbeds(result.toEmbed()).await()
    }

    private fun Punishment.toEmbed() = Embed {
        title = "Punishment $snowflake"
        field("Target ID", target.toString(), true)
        field("Type", type.toString(), true)
        field("Reason", reason, false)
        field("Timestamp", renderer.renderInstant(snowflake.timestamp), true)
        field("Duration", renderer.renderDuration(duration), true)
        if (pardon != null) {
            field("Pardon Reason", pardon!!.reason, false)
            field("Pardon Timestamp", renderer.renderInstant(pardon!!.timestamp), true)
        }
    }

    @ImperiumCommand(["ban"], Rank.MODERATOR)
    suspend fun onBanCommand(
        interaction: SlashCommandInteraction,
        player: Snowflake,
        reason: String,
        duration: PunishmentDuration = PunishmentDuration.THREE_DAYS
    ) {
        onPunishCommand("Banned", Punishment.Type.BAN, interaction, player, reason, duration.value)
    }

    @ImperiumCommand(["freeze"], Rank.MODERATOR)
    suspend fun onFreezeCommand(
        interaction: SlashCommandInteraction,
        player: Snowflake,
        reason: String,
        duration: PunishmentDuration = PunishmentDuration.THREE_HOURS
    ) {
        onPunishCommand(
            "Frozen", Punishment.Type.FREEZE, interaction, player, reason, duration.value)
    }

    @ImperiumCommand(["mute"], Rank.MODERATOR)
    suspend fun onMuteCommand(
        interaction: SlashCommandInteraction,
        player: Snowflake,
        reason: String,
        duration: PunishmentDuration = PunishmentDuration.ONE_DAY
    ) {
        onPunishCommand("Muted", Punishment.Type.MUTE, interaction, player, reason, duration.value)
    }

    private suspend fun onPunishCommand(
        verb: String,
        type: Punishment.Type,
        interaction: SlashCommandInteraction,
        player: Snowflake,
        reason: String,
        duration: Duration
    ) {
        val reply = interaction.deferReply(true).await()
        if (users.findBySnowflake(player) == null) {
            reply.sendMessage("Target is not a valid IP address, UUID or USER ID.").await()
        } else {
            punishments.punish(interaction.member!!.identity, player, reason, type, duration)
            reply.sendMessage("$verb user $player.").await()
        }
    }

    @ImperiumCommand(["pardon"], Rank.MODERATOR)
    suspend fun onPardonCommand(
        interaction: SlashCommandInteraction,
        punishment: String,
        reason: String
    ) {
        val reply = interaction.deferReply(true).await()
        val snowflake = punishment.toLongOrNull()
        if (snowflake == null) {
            reply.sendMessage("Invalid Punishment ID.").await()
            return
        }

        val entry = punishments.findBySnowflake(snowflake)
        if (entry == null) {
            reply.sendMessage("Punishment not found.").await()
            return
        }

        if (entry.pardon != null) {
            reply.sendMessage("Punishment already pardoned.").await()
            return
        }

        punishments.pardon(interaction.member!!.identity, snowflake, reason)
        reply.sendMessage("Pardoned user.").await()
    }
}
