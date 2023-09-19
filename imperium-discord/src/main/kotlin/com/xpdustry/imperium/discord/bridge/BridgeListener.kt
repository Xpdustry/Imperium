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
package com.xpdustry.imperium.discord.bridge

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.bridge.BridgeChatMessage
import com.xpdustry.imperium.common.bridge.MindustryPlayerMessage
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.subscribe
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.discord.service.DiscordService
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.javacord.api.entity.channel.ChannelCategory
import org.javacord.api.entity.message.MessageBuilder
import org.javacord.api.entity.message.mention.AllowedMentionsBuilder
import kotlin.jvm.optionals.getOrNull

class BridgeListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val discord = instances.get<DiscordService>()
    private val messenger = instances.get<Messenger>()
    private val config = instances.get<ServerConfig.Discord>()

    override fun onImperiumInit() {
        discord.getMainServer().addMessageCreateListener { event ->
            if (event.message.author.isBotUser || event.message.content.isBlank()) return@addMessageCreateListener
            val channel = event.channel.asServerTextChannel().getOrNull() ?: return@addMessageCreateListener
            if (channel.category.getOrNull()?.id == config.categories.liveChat) {
                ImperiumScope.MAIN.launch {
                    messenger.publish(BridgeChatMessage(channel.name, event.message.author.name, event.message.content))
                }
            }
        }

        messenger.subscribe<MindustryPlayerMessage> { message ->
            val channel = getLiveChatCategory().channels.find { it.name == message.serverName }
                ?: discord.getMainServer().createTextChannelBuilder()
                    .setCategory(getLiveChatCategory())
                    .setName(message.serverName)
                    .create()
                    .await()

            val textChannel = channel.asServerTextChannel().getOrNull()
            if (textChannel == null) {
                logger.error("Channel ${channel.name} (${channel.id}) is not a text channel")
                return@subscribe
            }

            val text = when (message) {
                is MindustryPlayerMessage.Join -> ":green_square: **${message.player.name}** has joined the server."
                is MindustryPlayerMessage.Quit -> ":red_square: **${message.player.name}** has left the server."
                is MindustryPlayerMessage.Chat -> ":blue_square: **${message.player.name}**: ${message.message}"
            }

            MessageBuilder()
                .setAllowedMentions(NO_MENTIONS)
                .setContent(text)
                .send(textChannel)
                .await()
        }
    }

    private fun getLiveChatCategory(): ChannelCategory =
        discord.getMainServer().getChannelCategoryById(config.categories.liveChat).get()

    companion object {
        private val logger by LoggerDelegate()
        private val NO_MENTIONS = AllowedMentionsBuilder()
            .setMentionEveryoneAndHere(false)
            .setMentionRoles(false)
            .setMentionUsers(false)
            .build()
    }
}
