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
package com.xpdustry.imperium.mindustry.account

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.gui.Action
import com.xpdustry.distributor.api.gui.menu.MenuManager
import com.xpdustry.distributor.api.gui.menu.MenuOption
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.component1
import com.xpdustry.imperium.mindustry.misc.component2
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.key
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.translation.gui_close
import com.xpdustry.imperium.mindustry.translation.gui_user_settings_description
import com.xpdustry.imperium.mindustry.translation.gui_user_settings_entry
import com.xpdustry.imperium.mindustry.translation.gui_user_settings_title
import com.xpdustry.imperium.mindustry.translation.user_setting_description
import kotlinx.coroutines.launch

class UserSettingsCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val users = instances.get<UserManager>()
    private val playerSettingsInterface = createPlayerSettingsInterface(instances.get())

    @ImperiumCommand(["settings"])
    @ClientSide
    suspend fun onUserSettingsCommand(sender: CommandSender) {
        val settings = loadUserSettings(sender.player.uuid())
        runMindustryThread {
            val window = playerSettingsInterface.create(sender.player)
            window.state[SETTINGS] = settings
            window.show()
        }
    }

    private fun createPlayerSettingsInterface(plugin: MindustryPlugin): MenuManager =
        MenuManager.create(plugin).addTransformer { (pane, state) ->
            pane.title = gui_user_settings_title()
            pane.content = gui_user_settings_description()
            state[SETTINGS]!!
                .entries
                .sortedBy { it.key.name }
                .forEach { (setting, value) ->
                    pane.grid.addRow(
                        MenuOption.of(gui_user_settings_entry(setting, value)) { window ->
                            val settings = window.state[SETTINGS]!!.toMutableMap()
                            settings[setting] = !value
                            ImperiumScope.MAIN.launch {
                                users.setSettings(window.viewer.identity, settings)
                                runMindustryThread {
                                    window.state[SETTINGS] = settings
                                    window.show()
                                }
                            }
                        })
                    pane.grid.addRow(
                        MenuOption.of(user_setting_description(setting), Action.none()))
                }
            pane.grid.addRow(MenuOption.of(gui_close(), Action.back()))
        }

    private suspend fun loadUserSettings(uuid: String): Map<User.Setting, Boolean> {
        val settings = users.getSettings(uuid).toMutableMap()
        for (setting in User.Setting.entries) {
            settings.putIfAbsent(setting, setting.default)
        }
        return settings
    }

    companion object {
        private val SETTINGS = key<Map<User.Setting, Boolean>>("settings")
    }
}
