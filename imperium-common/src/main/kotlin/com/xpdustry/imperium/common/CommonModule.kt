/*
 * Imperium, the software collection powering the Xpdustry network.
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
 *
 */
package com.xpdustry.imperium.common

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.SimpleAccountManager
import com.xpdustry.imperium.common.bridge.PlayerTracker
import com.xpdustry.imperium.common.bridge.RequestingPlayerTracker
import com.xpdustry.imperium.common.config.DatabaseConfig
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.ImperiumConfigFactory
import com.xpdustry.imperium.common.config.MessengerConfig
import com.xpdustry.imperium.common.config.NetworkConfig
import com.xpdustry.imperium.common.config.TranslatorConfig
import com.xpdustry.imperium.common.config.WebhookConfig
import com.xpdustry.imperium.common.content.MindustryMapManager
import com.xpdustry.imperium.common.content.SimpleMindustryMapManager
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.database.SimpleSQLProvider
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.inject.module
import com.xpdustry.imperium.common.inject.single
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.RabbitmqMessenger
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.common.network.SimpleDiscovery
import com.xpdustry.imperium.common.network.VpnApiIoDetection
import com.xpdustry.imperium.common.network.VpnDetection
import com.xpdustry.imperium.common.security.AddressWhitelist
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.security.SimpleAddressWhitelist
import com.xpdustry.imperium.common.security.SimplePunishmentManager
import com.xpdustry.imperium.common.snowflake.SimpleSnowflakeGenerator
import com.xpdustry.imperium.common.snowflake.SnowflakeGenerator
import com.xpdustry.imperium.common.time.SimpleTimeRenderer
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.common.translator.DeeplTranslator
import com.xpdustry.imperium.common.translator.Translator
import com.xpdustry.imperium.common.user.SimpleUserManager
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.common.version.ImperiumVersion
import com.xpdustry.imperium.common.webhook.DiscordWebhookMessageSender
import com.xpdustry.imperium.common.webhook.WebhookMessageSender
import java.util.concurrent.Executors
import java.util.function.Supplier
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

@Suppress("FunctionName")
fun CommonModule() =
    module("common") {
        single(ImperiumConfigFactory())

        single<Translator> {
            val config = get<ImperiumConfig>().translator
            when (config) {
                is TranslatorConfig.None -> Translator.Noop
                is TranslatorConfig.DeepL -> DeeplTranslator(get(), get())
            }
        }

        single<Discovery> { SimpleDiscovery(get(), get("discovery"), get()) }

        single<VpnDetection> {
            when (val config = get<ImperiumConfig>().network.vpnDetection) {
                is NetworkConfig.VpnDetectionConfig.None -> VpnDetection.Noop
                is NetworkConfig.VpnDetectionConfig.VpnApiIo -> VpnApiIoDetection(config, get())
            }
        }

        single<Messenger> {
            when (get<ImperiumConfig>().messenger) {
                is MessengerConfig.RabbitMQ -> RabbitmqMessenger(get())
            }
        }

        single<SQLProvider> {
            when (val config = get<ImperiumConfig>().database) {
                is DatabaseConfig.SQL -> SimpleSQLProvider(config, get("directory"))
            }
        }

        single<AccountManager> { SimpleAccountManager(get(), get(), get()) }

        single<MindustryMapManager> { SimpleMindustryMapManager(get(), get(), get(), get()) }

        single<PunishmentManager> { SimplePunishmentManager(get(), get(), get(), get(), get()) }

        single<UserManager> { SimpleUserManager(get(), get(), get()) }

        single<OkHttpClient> {
            OkHttpClient.Builder()
                .connectTimeout(20.seconds.toJavaDuration())
                .connectTimeout(20.seconds.toJavaDuration())
                .readTimeout(20.seconds.toJavaDuration())
                .writeTimeout(20.seconds.toJavaDuration())
                .dispatcher(
                    Dispatcher(
                        // The default executor blocks the exit in Mindustry
                        Executors.newFixedThreadPool(
                            Runtime.getRuntime().availableProcessors(),
                            ThreadFactoryBuilder()
                                .setDaemon(true)
                                .setNameFormat("OkHttpClient-Dispatcher-Thread-%d")
                                .build())))
                .build()
        }

        single<SnowflakeGenerator> { SimpleSnowflakeGenerator(get()) }

        single<Supplier<Discovery.Data>>("discovery") { Supplier { Discovery.Data.Unknown } }

        single<ImperiumVersion> { ImperiumVersion(1, 1, 1) }

        single<PlayerTracker> { RequestingPlayerTracker(get()) }

        single<TimeRenderer> { SimpleTimeRenderer(get()) }

        single<WebhookMessageSender> {
            when (val webhookConfig = get<ImperiumConfig>().webhook) {
                is WebhookConfig.None -> WebhookMessageSender.None
                is WebhookConfig.Discord ->
                    DiscordWebhookMessageSender(get(), get(), webhookConfig, get())
            }
        }

        single<AddressWhitelist> { SimpleAddressWhitelist(get()) }
    }
