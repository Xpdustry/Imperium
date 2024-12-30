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
package com.xpdustry.imperium.mindustry

import arc.Application
import arc.ApplicationListener
import arc.Core
import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.annotation.PluginAnnotationProcessor
import com.xpdustry.distributor.api.component.render.ComponentRendererProvider
import com.xpdustry.distributor.api.permission.rank.RankPermissionSource
import com.xpdustry.distributor.api.permission.rank.RankProvider
import com.xpdustry.distributor.api.plugin.AbstractMindustryPlugin
import com.xpdustry.distributor.api.translation.BundleTranslationSource
import com.xpdustry.distributor.api.translation.ResourceBundles
import com.xpdustry.distributor.api.translation.TranslationSource
import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.imperium.common.application.BaseImperiumApplication
import com.xpdustry.imperium.common.application.ExitStatus
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.registerApplication
import com.xpdustry.imperium.common.registerCommonModule
import com.xpdustry.imperium.common.webhook.WebhookMessage
import com.xpdustry.imperium.common.webhook.WebhookMessageSender
import com.xpdustry.imperium.mindustry.account.AccountCommand
import com.xpdustry.imperium.mindustry.account.AccountListener
import com.xpdustry.imperium.mindustry.account.AchievementCommand
import com.xpdustry.imperium.mindustry.account.UserSettingsCommand
import com.xpdustry.imperium.mindustry.chat.BridgeChatMessageListener
import com.xpdustry.imperium.mindustry.chat.ChatCommand
import com.xpdustry.imperium.mindustry.chat.FlexListener
import com.xpdustry.imperium.mindustry.chat.HereCommand
import com.xpdustry.imperium.mindustry.command.CommandAnnotationScanner
import com.xpdustry.imperium.mindustry.command.HelpCommand
import com.xpdustry.imperium.mindustry.component.ImperiumComponentRendererProvider
import com.xpdustry.imperium.mindustry.config.ConventionListener
import com.xpdustry.imperium.mindustry.control.ControlListener
import com.xpdustry.imperium.mindustry.game.AlertListener
import com.xpdustry.imperium.mindustry.game.AntiGriefListener
import com.xpdustry.imperium.mindustry.game.ChangelogCommand
import com.xpdustry.imperium.mindustry.game.GameListener
import com.xpdustry.imperium.mindustry.game.ImperiumLogicListener
import com.xpdustry.imperium.mindustry.game.LogicListener
import com.xpdustry.imperium.mindustry.game.PauseListener
import com.xpdustry.imperium.mindustry.game.RatingListener
import com.xpdustry.imperium.mindustry.game.TeamCommand
import com.xpdustry.imperium.mindustry.game.TipListener
import com.xpdustry.imperium.mindustry.game.formation.FormationListener
import com.xpdustry.imperium.mindustry.history.HistoryCommand
import com.xpdustry.imperium.mindustry.metrics.MetricsListener
import com.xpdustry.imperium.mindustry.misc.ImperiumMetadataChunkReader
import com.xpdustry.imperium.mindustry.misc.getMindustryVersion
import com.xpdustry.imperium.mindustry.permission.ImperiumRankPermissionSource
import com.xpdustry.imperium.mindustry.permission.ImperiumRankProvider
import com.xpdustry.imperium.mindustry.security.AdminRequestListener
import com.xpdustry.imperium.mindustry.security.AntiEvadeListener
import com.xpdustry.imperium.mindustry.security.GatekeeperListener
import com.xpdustry.imperium.mindustry.security.ModerationCommand
import com.xpdustry.imperium.mindustry.security.NoHornyListener
import com.xpdustry.imperium.mindustry.security.PunishmentListener
import com.xpdustry.imperium.mindustry.security.ReportCommand
import com.xpdustry.imperium.mindustry.security.VoteKickCommand
import com.xpdustry.imperium.mindustry.telemetry.DumpCommand
import com.xpdustry.imperium.mindustry.tower.TowerListener
import com.xpdustry.imperium.mindustry.world.CoreBlockListener
import com.xpdustry.imperium.mindustry.world.ExcavateCommand
import com.xpdustry.imperium.mindustry.world.HubListener
import com.xpdustry.imperium.mindustry.world.KillAllCommand
import com.xpdustry.imperium.mindustry.world.MapListener
import com.xpdustry.imperium.mindustry.world.ResourceHudListener
import com.xpdustry.imperium.mindustry.world.RockTheVoteCommand
import com.xpdustry.imperium.mindustry.world.SaveCommand
import com.xpdustry.imperium.mindustry.world.SpawnCommand
import com.xpdustry.imperium.mindustry.world.SwitchCommand
import com.xpdustry.imperium.mindustry.world.WaveCommand
import com.xpdustry.imperium.mindustry.world.WelcomeListener
import com.xpdustry.imperium.mindustry.world.WorldEditCommand
import kotlin.reflect.KClass
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import mindustry.io.SaveVersion

class ImperiumPlugin : AbstractMindustryPlugin() {
    private val application = MindustryImperiumApplication()

    override fun onInit() {
        // https://github.com/Anuken/Arc/pull/158
        if (getMindustryVersion().build < 147) {
            Core.app =
                object : Application by Core.app {
                    override fun removeListener(listener: ApplicationListener) {
                        post { synchronized(listeners) { listeners.remove(listener) } }
                    }
                }
        }
    }

    override fun onLoad() {
        SaveVersion.addCustomChunk("imperium", ImperiumMetadataChunkReader)

        with(application.instances) {
            registerApplication(application)
            registerCommonModule()
            registerMindustryModule(this@ImperiumPlugin)
            createAll()
        }

        registerService(
            RankProvider::class,
            ImperiumRankProvider(application.instances.get()).also(application::register))

        registerService(
            RankPermissionSource::class, ImperiumRankPermissionSource(application.instances.get()))

        registerService(
            TranslationSource::class,
            BundleTranslationSource.create(application.instances.get<ImperiumConfig>().language)
                .apply {
                    registerAll(
                        ResourceBundles.fromClasspathDirectory(
                            ImperiumPlugin::class.java,
                            "com/xpdustry/imperium/mindustry/bundles/",
                            "bundle",
                        ),
                        ResourceBundles::getMessageFormatTranslation)
                })

        registerService(
            ComponentRendererProvider::class,
            ImperiumComponentRendererProvider(application.instances.get()))

        sequenceOf(
                ConventionListener::class,
                GatekeeperListener::class,
                AccountListener::class,
                AccountCommand::class,
                ChatCommand::class,
                HistoryCommand::class,
                BridgeChatMessageListener::class,
                ReportCommand::class,
                NoHornyListener::class,
                AdminRequestListener::class,
                PunishmentListener::class,
                MapListener::class,
                VoteKickCommand::class,
                ExcavateCommand::class,
                RockTheVoteCommand::class,
                CoreBlockListener::class,
                HelpCommand::class,
                WaveCommand::class,
                KillAllCommand::class,
                DumpCommand::class,
                SwitchCommand::class,
                UserSettingsCommand::class,
                WelcomeListener::class,
                ResourceHudListener::class,
                ImperiumLogicListener::class,
                AntiEvadeListener::class,
                GameListener::class,
                TipListener::class,
                RatingListener::class,
                SpawnCommand::class,
                WorldEditCommand::class,
                HereCommand::class,
                ModerationCommand::class,
                AlertListener::class,
                TeamCommand::class,
                FormationListener::class,
                ControlListener::class,
                PauseListener::class,
                AchievementCommand::class,
                LogicListener::class,
                SaveCommand::class,
                AntiGriefListener::class,
                FlexListener::class,
                MetricsListener::class,
                ChangelogCommand::class)
            .forEach(application::register)

        val gamemode = application.instances.get<ImperiumConfig>().mindustry.gamemode
        if (gamemode == MindustryGamemode.HUB) {
            application.register(HubListener::class)
        } else {
            Core.settings.remove("totalPlayers")
        }
        if (gamemode == MindustryGamemode.TOWER_DEFENSE) {
            application.register(TowerListener::class)
        }

        application.init()

        val processor =
            PluginAnnotationProcessor.compose(
                CommandAnnotationScanner(this, application.instances.get()),
                PluginAnnotationProcessor.tasks(this),
                PluginAnnotationProcessor.events(this),
                PluginAnnotationProcessor.triggers(this))

        application.listeners.forEach(processor::process)

        runBlocking {
            application.instances
                .get<WebhookMessageSender>()
                .send(WebhookMessage(content = "The server has started."))
        }

        logger.info("Imperium plugin Loaded!")
    }

    override fun onExit() {
        application.exit(ExitStatus.EXIT)
    }

    private fun <T : Any> registerService(klass: KClass<T>, instance: T) {
        Distributor.get()
            .serviceManager
            .register(this@ImperiumPlugin, klass.java, instance, Priority.NORMAL)
    }

    private inner class MindustryImperiumApplication : BaseImperiumApplication(logger) {
        private var exited = false

        override fun exit(status: ExitStatus) {
            if (exited) return
            exited = true
            runBlocking {
                instances
                    .get<WebhookMessageSender>()
                    .send(WebhookMessage(content = "The server is exiting with $status code."))
            }
            super.exit(status)
            when (status) {
                ExitStatus.EXIT,
                ExitStatus.INIT_FAILURE -> Core.app.exit()
                ExitStatus.RESTART -> Core.app.restart()
            }
        }
    }
}

// Very hacky way to restart the server
private fun Application.restart() {
    exit()
    addListener(
        object : ApplicationListener {
            override fun dispose() {
                Core.settings.autosave()
                exitProcess(2)
            }
        })
}
