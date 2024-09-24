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
import com.xpdustry.imperium.common.misc.toInetAddressOrNull
import com.xpdustry.imperium.common.security.AddressWhitelist
import com.xpdustry.imperium.discord.misc.await
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction

class WhitelistCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val whitelist = instances.get<AddressWhitelist>()

    @ImperiumCommand(["whitelist", "add"], Rank.ADMIN)
    suspend fun onWhitelistAddCommand(interaction: SlashCommandInteraction, address: String) {
        val reply = interaction.deferReply(true).await()
        // TODO Add InetAddress parser, with option to prevent the use of loopback addresses
        val ip = address.toInetAddressOrNull()
        if (ip == null) {
            reply.sendMessage("The ip address is not valid.").await()
        } else if (whitelist.containsAddress(ip)) {
            reply.sendMessage("The whitelist already contains this address.").await()
        } else {
            whitelist.addAddress(ip)
            reply.sendMessage("Added address to whitelist.").await()
        }
    }

    @ImperiumCommand(["whitelist", "remove"], Rank.ADMIN)
    suspend fun onWhitelistRemoveCommand(interaction: SlashCommandInteraction, address: String) {
        val reply = interaction.deferReply(true).await()
        val ip = address.toInetAddressOrNull()
        if (ip == null) {
            reply.sendMessage("The ip address is not valid.").await()
        } else if (whitelist.containsAddress(ip)) {
            whitelist.removeAddress(ip)
            reply.sendMessage("Removed address from whitelist.").await()
        } else {
            reply.sendMessage("The whitelist does not contain this address.").await()
        }
    }
}
