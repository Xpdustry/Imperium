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
package com.xpdustry.imperium.discord.content

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.content.MindustryMap
import com.xpdustry.imperium.common.image.inputStream
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.MINDUSTRY_ACCENT_COLOR
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.discord.misc.Embed
import com.xpdustry.imperium.discord.misc.MessageCreate
import com.xpdustry.imperium.discord.misc.addSuspendingEventListener
import com.xpdustry.imperium.discord.misc.await
import com.xpdustry.imperium.discord.misc.awaitVoid
import com.xpdustry.imperium.discord.service.DiscordService
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.future.await
import mindustry.Vars
import mindustry.game.Schematic
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.FileUpload

class MindustryContentListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val discord = instances.get<DiscordService>()
    private val content = instances.get<MindustryContentHandler>()
    private val guild: Guild? = null

    override fun onImperiumInit() {
        discord.jda.addSuspendingEventListener<MessageReceivedEvent> { event ->
            if (event.author.isBot || event.author.isSystem || event.isWebhookMessage) {
                return@addSuspendingEventListener
            }
            if (event.isFromType(ChannelType.TEXT)) {
                onMindustryContent(event.channel.asTextChannel(), event.message, event.member!!)
            }
        }
    }

    private suspend fun onMindustryContent(channel: TextChannel, message: Message, member: Member) {
        val maps = mutableListOf<Triple<MapMetadata, BufferedImage, Message.Attachment>>()
        val schematics = mutableListOf<Schematic>()
        var delete = false

        if (message.contentRaw.startsWith(Vars.schematicBaseStart)) {
            delete = true
            schematics +=
                content
                    .getSchematic(message.contentRaw)
                    .onFailure {
                        channel.sendMessage("Failed to parse text schematic ${it.message}").await()
                        return
                    }
                    .getOrThrow()
        }

        val attachements = message.attachments
        if (attachements.isNotEmpty() && message.contentRaw.isEmpty()) {
            delete = true
        }

        message.attachments.forEach { attachment ->
            try {
                if (attachment.fileExtension == "txt") {
                    if (attachment.size > SCHEMATIC_MAX_FILE_SIZE) {
                        channel.sendMessage("Schematic file is too large!").await()
                        return
                    }
                    val text =
                        attachment.proxy.download().await().bufferedReader().use { it.readText() }
                    if (text.startsWith(Vars.schematicBaseStart)) {
                        schematics +=
                            content
                                .getSchematic(text)
                                .onFailure {
                                    channel
                                        .sendMessage(
                                            "Failed to parse text schematic: ${it.message}")
                                        .await()
                                    return
                                }
                                .getOrThrow()
                    }
                } else if (attachment.fileExtension == "msch") {
                    if (attachment.size > SCHEMATIC_MAX_FILE_SIZE) {
                        channel.sendMessage("Schematic file is too large!").await()
                        return
                    }
                    attachment.proxy.download().await().use { stream ->
                        schematics +=
                            content
                                .getSchematic(stream)
                                .onFailure {
                                    channel
                                        .sendMessage(
                                            "Failed to parse binary schematic: ${it.message}")
                                        .await()
                                    return
                                }
                                .getOrThrow()
                    }
                } else if (attachment.fileExtension == "msav") {
                    if (attachment.size > MindustryMap.MAX_MAP_FILE_SIZE) {
                        channel
                            .sendMessage(
                                "The map file is too big, please submit reasonably sized maps.")
                            .await()
                        return
                    }
                    attachment.proxy.download().await().use { stream ->
                        val (meta, preview) =
                            content
                                .getMapMetadataWithPreview(stream)
                                .onFailure {
                                    channel
                                        .sendMessage("Failed to parse map: ${it.message}")
                                        .await()
                                    return
                                }
                                .getOrThrow()

                        if (meta.width > MindustryMap.MAX_MAP_SIDE_SIZE ||
                            meta.height > MindustryMap.MAX_MAP_SIDE_SIZE) {
                            channel
                                .sendMessage(
                                    "The map is bigger than ${MindustryMap.MAX_MAP_SIDE_SIZE} blocks, please submit reasonably sized maps.")
                                .await()
                            return
                        }
                        maps += Triple(meta, preview, attachment)
                    }
                } else {
                    delete = false
                }
            } catch (e: Exception) {
                channel.sendMessage("Failed to parse mindustry content: ${e.message}").await()
                return
            }
        }

        schematics.forEach { schematic ->
            val stream = ByteArrayOutputStream()
            content
                .writeSchematic(schematic, stream)
                .onFailure {
                    channel.sendMessage("Failed to write the schematic: ${it.message}").await()
                    return
                }
                .getOrThrow()
            val preview =
                content
                    .getSchematicPreview(schematic)
                    .onFailure {
                        channel
                            .sendMessage("${"Failed to generate schematic preview"}: ${it.message}")
                            .await()
                        return
                    }
                    .getOrThrow()
            val cost = StringBuilder()
            for (stack in schematic.requirements()) {
                // Requires you to upload emotes with all the item names eg: "blastcompound"
                val emotes = member.guild.getEmojisByName(stack.item.name.replace("-", ""), true)
                val emote = emotes.getOrNull(0) as? CustomEmoji
                val result = if (emote != null) emote.getAsMention() else ":question:"

                cost.append(result)
                cost.append(stack.amount).append(" ")
            }

            channel
                .sendMessage(
                    MessageCreate {
                        files +=
                            FileUpload.fromData(
                                stream.toByteArray(),
                                "${schematic.name().stripMindustryColors()}.msch")
                        files += FileUpload.fromStreamSupplier("preview.png", preview::inputStream)
                        embeds += Embed {
                            author(member)
                            color = MINDUSTRY_ACCENT_COLOR.rgb
                            title = schematic.name().stripMindustryColors()
                            field("Requirements", cost.toString().trim())
                            description = schematic.description().stripMindustryColors()
                            image = "attachment://preview.png"
                        }
                    })
                .await()
        }

        maps.forEach { (meta, preview, attachement) ->
            channel
                .sendMessage(
                    MessageCreate {
                        files +=
                            FileUpload.fromStreamSupplier(
                                meta.name.stripMindustryColors() + ".msch") {
                                    attachement.proxy.download().join()
                                }
                        files += FileUpload.fromStreamSupplier("preview.png", preview::inputStream)
                        embeds += Embed {
                            color = MINDUSTRY_ACCENT_COLOR.rgb
                            title = meta.name.stripMindustryColors()
                            image = "attachment://preview.png"
                            field("Author", meta.author?.stripMindustryColors() ?: "Unknown", false)
                            field(
                                "Description",
                                meta.description?.stripMindustryColors() ?: "Unknown",
                                false)
                            field("Size", "${preview.width} x ${preview.height}", false)
                        }
                    })
                .await()
        }

        if (delete) {
            channel.deleteMessageById(message.idLong).awaitVoid()
        }
    }

    companion object {
        private const val SCHEMATIC_MAX_FILE_SIZE = 2 * 1024 * 1024 // 2MB
    }
}
