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

import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.discord.misc.await
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction

class AccountCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val accounts = instances.get<AccountManager>()

    @ImperiumCommand(["account", "rank", "set"], Rank.OWNER)
    suspend fun onAccountRankSet(interaction: SlashCommandInteraction, target: String, rank: Rank) {
        val reply = interaction.deferReply(true).await()
        if (rank == Rank.OWNER) {
            reply.sendMessage("Nuh huh").await()
            return
        }
        if (target.toLongOrNull() == null) {
            reply.sendMessage("Invalid target.").await()
            return
        }
        val snowflake =
            if (accounts.existsBySnowflake(target.toLong())) {
                target.toLong()
            } else {
                accounts.findByDiscord(target.toLong())?.snowflake
            }

        if (snowflake == null) {
            reply.sendMessage("Account not found.").await()
            return
        }

        accounts.setRank(snowflake, rank)
        reply.sendMessage("Set rank to $rank.").await()
    }
}
