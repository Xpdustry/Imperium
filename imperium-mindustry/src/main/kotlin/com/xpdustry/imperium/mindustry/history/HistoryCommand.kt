/*
 * Imperium, the software collection powering the Xpdustry network.
 * Copyright (C) 2023  Xpdustry
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
package com.xpdustry.imperium.mindustry.history

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.command.annotation.Max
import com.xpdustry.imperium.common.command.annotation.Min
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.common.misc.toHexString
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import fr.xpdustry.distributor.api.command.sender.CommandSender
import fr.xpdustry.distributor.api.event.EventHandler
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType
import mindustry.net.Administration.PlayerInfo

class HistoryCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val history = instances.get<BlockHistory>()
    // TODO Have a general click manager to avoid collisions with others
    private val taps = PlayerMap<Long>(instances.get())
    private val users = instances.get<UserManager>()
    private val config = instances.get<ServerConfig.Mindustry>()
    private val renderer = instances.get<TimeRenderer>()

    @Command(["history", "player"])
    @ClientSide
    @ServerSide
    private fun onPlayerHistoryCommand(
        sender: CommandSender,
        player: PlayerInfo,
        @Min(1) @Max(50) limit: Int = 10
    ) {
        val entries = normalize(history.getHistory(player.id), limit)
        if (entries.none()) {
            sender.sendWarning("No history found.")
            return
        }
        val builder =
            StringBuilder("[accent]History of player [white]").append(player.plainLastName())
        if (canSeeUuid(sender)) {
            builder.append(" [accent](").append(player.id).append(")")
        }
        builder.append(":")
        for (entry in entries) {
            builder
                .append("\n[accent] > ")
                .append(
                    renderEntry(entry, name = false, uuid = false, position = true, indent = 3),
                )
        }

        sender.sendMessage(
            if (sender.isConsole) builder.toString().stripMindustryColors() else builder.toString())
    }

    @EventHandler
    internal fun onPlayerTapEvent(event: EventType.TapEvent) =
        ImperiumScope.MAIN.launch {
            if (users.getSetting(event.player.uuid(), User.Setting.DOUBLE_TAP_TILE_LOG)) {
                val last = taps[event.player]
                if (last != null &&
                    (System.currentTimeMillis() - last).milliseconds <
                        config.history.doubleClickDelay) {
                    taps.remove(event.player)
                    runMindustryThread {
                        onTileHistoryCommand(
                            CommandSender.player(event.player), event.tile.x, event.tile.y)
                    }
                } else {
                    taps[event.player] = System.currentTimeMillis()
                }
            }
        }

    @Command(["history", "tile"])
    @ClientSide
    @ServerSide
    private fun onTileHistoryCommand(
        sender: CommandSender,
        @Min(1) x: Short,
        @Min(1) y: Short,
        @Min(1) @Max(50) limit: Int = 10,
    ) {
        val entries = normalize(history.getHistory(x.toInt(), y.toInt()), limit)
        if (entries.none()) {
            sender.sendWarning("No history found.")
            return
        }
        val builder =
            StringBuilder("[accent]History of tile [white]")
                .append("(")
                .append(x)
                .append(", ")
                .append(y)
                .append(")[]:")
        for (entry in entries) {
            builder
                .append("\n[accent] > ")
                .append(renderEntry(entry, true, canSeeUuid(sender), false, 3))
        }

        sender.sendMessage(
            if (sender.isConsole) builder.toString().stripMindustryColors() else builder.toString())
    }

    private fun renderEntry(
        entry: HistoryEntry,
        name: Boolean,
        uuid: Boolean,
        position: Boolean,
        indent: Int
    ): String {
        val builder = StringBuilder("[white]")
        if (name) {
            builder.append(getName(entry.author))
            if (uuid && entry.author.uuid != null) {
                builder.append(" [gray](").append(entry.author.uuid).append(")")
            }
            builder.append("[white]: ")
        }
        when (entry.type) {
            HistoryEntry.Type.PLACING ->
                builder.append("Began construction of [accent]").append(entry.block.name)
            HistoryEntry.Type.PLACE ->
                builder.append("Constructed [accent]").append(entry.block.name)
            HistoryEntry.Type.BREAKING ->
                builder.append("Began deconstruction of [accent]").append(entry.block.name)
            HistoryEntry.Type.BREAK ->
                builder.append("Deconstructed [accent]").append(entry.block.name)
            HistoryEntry.Type.ROTATE ->
                builder
                    .append("Set direction of [accent]")
                    .append(entry.block.name)
                    .append(" [white]to [accent]")
                    .append(getOrientation(entry.rotation))
            HistoryEntry.Type.CONFIGURE ->
                renderConfiguration(
                    builder,
                    entry,
                    entry.configuration!!,
                    indent,
                )
        }
        if (entry.type !== HistoryEntry.Type.CONFIGURE && entry.configuration != null) {
            renderConfiguration(
                builder.append(" ".repeat(indent)).append("\n[accent] > [white]"),
                entry,
                entry.configuration,
                indent + 3,
            )
        }
        builder.append("[white]")
        if (position) {
            builder.append(" at [accent](").append(entry.x).append(", ").append(entry.y).append(")")
        }
        builder.append(", [white]").append(renderer.renderRelativeInstant(entry.timestamp))
        return builder.toString()
    }

    private fun renderConfiguration(
        builder: StringBuilder,
        entry: HistoryEntry,
        config: HistoryConfig,
        ident: Int,
    ) {
        when (config) {
            is HistoryConfig.Composite -> {
                builder.append("Configured [accent]").append(entry.block.name).append("[white]:")
                for (component in config.configurations) {
                    renderConfiguration(
                        builder.append("\n").append(" ".repeat(ident)).append("[accent] - [white]"),
                        entry,
                        component,
                        ident + 3,
                    )
                }
            }
            is HistoryConfig.Text -> {
                builder
                    .append("Changed the [accent]")
                    .append(config.type.name.lowercase())
                    .append("[white] of [accent]")
                    .append(entry.block.name)
                if (config.type === HistoryConfig.Text.Type.MESSAGE) {
                    builder.append("[white] to [gray]").append(config.text)
                }
            }
            is HistoryConfig.Link -> {
                if (config.type === HistoryConfig.Link.Type.RESET) {
                    builder.append("Reset the links of [accent]").append(entry.block.name)
                    return
                }
                builder
                    .append(
                        if (config.type === HistoryConfig.Link.Type.CONNECT) "Connected"
                        else "Disconnected")
                    .append(" [accent]")
                    .append(entry.block.name)
                    .append("[white] ")
                    .append(if (config.type === HistoryConfig.Link.Type.CONNECT) "to" else "from")
                    .append(" [accent]")
                    .append(
                        config.positions.joinToString(", ") { point ->
                            "(${(point.x + entry.buildX)}, ${(point.y + entry.buildY)})"
                        },
                    )
            }
            is HistoryConfig.Canvas -> {
                builder.append("Changed the content of [accent]").append(entry.block.name)
            }
            is HistoryConfig.Content -> {
                if (config.value == null) {
                    builder.append("Reset the content of [accent]").append(entry.block.name)
                    return
                }
                builder
                    .append("Configured [accent]")
                    .append(entry.block.name)
                    .append("[white] to [accent]")
                    .append(config.value.name)
            }
            is HistoryConfig.Enable -> {
                builder
                    .append(if (config.value) "Enabled" else "Disabled")
                    .append(" [accent]")
                    .append(entry.block.name)
            }
            is HistoryConfig.Light -> {
                builder
                    .append("Configured [accent]")
                    .append(entry.block.name)
                    .append("[white] to [accent]")
                    .append(config.color.toHexString())
            }
            is HistoryConfig.Simple -> {
                builder
                    .append("Configured [accent]")
                    .append(entry.block.name)
                    .append("[white] to [accent]")
                    .append(config.value?.toString() ?: "null")
            }
        }
    }

    private fun getName(author: HistoryAuthor): String {
        return if (author.uuid != null) Vars.netServer.admins.getInfo(author.uuid).lastName
        else author.team.name.lowercase() + " " + author.unit.name
    }

    // TODO Use our permission system
    private fun canSeeUuid(sender: CommandSender): Boolean =
        sender.isConsole || sender.player.admin()

    // First we sort by timestamp from latest to earliest, then we take the first N elements,
    // then we reverse the list so the latest entries are at the end
    private fun normalize(entries: List<HistoryEntry>, limit: Int) =
        entries
            .asSequence()
            .sortedByDescending(HistoryEntry::timestamp)
            .take(limit)
            .sortedBy(HistoryEntry::timestamp)

    private fun getOrientation(rotation: Int): String =
        when (rotation % 4) {
            0 -> "right"
            1 -> "top"
            2 -> "left"
            3 -> "bottom"
            else -> error("This should never happen")
        }
}
