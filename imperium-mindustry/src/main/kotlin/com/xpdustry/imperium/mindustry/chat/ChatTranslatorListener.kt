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
package com.xpdustry.imperium.mindustry.chat

import com.xpdustry.distributor.annotation.method.EventHandler
import com.xpdustry.distributor.util.Priority
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.common.translator.Translator
import com.xpdustry.imperium.common.translator.TranslatorResult
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.misc.javaLocale
import kotlinx.coroutines.withTimeoutOrNull
import mindustry.game.EventType.PlayerJoin
import mindustry.gen.Player

class ChatTranslatorListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ImperiumConfig>()
    private val translator = instances.get<Translator>()
    private val users = instances.get<UserManager>()
    private val pipeline = instances.get<ChatMessagePipeline>()

    override fun onImperiumInit() {
        pipeline.register("translator", Priority.LOW) { (sender, target, message) ->
            if (target != null && !users.getSetting(target.uuid(), User.Setting.CHAT_TRANSLATOR)) {
                return@register message
            }

            val sourceLocale = sender?.let(Player::javaLocale) ?: config.language
            val targetLocale = target?.let(Player::javaLocale) ?: config.language
            val rawMessage = message.stripMindustryColors()

            val result =
                withTimeoutOrNull(3000L) {
                    translator.translate(rawMessage, sourceLocale, targetLocale)
                }

            when (result) {
                is TranslatorResult.UnsupportedLanguage ->
                    logger.debug(
                        "Warning: The locale {} is not supported by the chat translator",
                        result.locale)
                is TranslatorResult.Failure ->
                    logger.error(
                        "Failed to translate the message '{}' from {} to {}",
                        rawMessage,
                        sourceLocale,
                        targetLocale,
                        result.exception)
                is TranslatorResult.RateLimited ->
                    logger.debug("Warning: The chat translator is rate limited")
                null ->
                    logger.error(
                        "Failed to translate the message '{}' from {} to {} due to timeout",
                        rawMessage,
                        sourceLocale,
                        targetLocale)
                is TranslatorResult.Success -> {
                    return@register if (rawMessage.lowercase(sourceLocale) ==
                        result.text.lowercase(targetLocale))
                        message
                    else "$message [lightgray](${result.text})"
                }
            }

            message
        }
    }

    @EventHandler
    fun onPlayerConnect(event: PlayerJoin) {
        if (translator.isSupportedLanguage(event.player.javaLocale)) {
            event.player.sendMessage(
                "[green]The chat translator supports your language, you can talk in your native tongue!")
        } else {
            event.player.sendMessage(
                "[scarlet]Warning, your language is not supported by the chat translator. Please talk in english.")
        }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
