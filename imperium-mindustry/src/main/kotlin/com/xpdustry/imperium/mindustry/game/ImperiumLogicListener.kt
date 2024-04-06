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
package com.xpdustry.imperium.mindustry.game

import com.xpdustry.distributor.DistributorProvider
import com.xpdustry.distributor.plugin.MindustryPlugin
import com.xpdustry.distributor.util.Priority
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import mindustry.core.GameState
import mindustry.game.EventType.StateChangeEvent

class ImperiumLogicListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val plugin = instances.get<MindustryPlugin>()

    override fun onImperiumInit() {
        DistributorProvider.get().eventBus.subscribe(
            StateChangeEvent::class.java, Priority.HIGH, plugin) { event ->
                if (event.from == GameState.State.menu && event.to == GameState.State.playing) {
                    DistributorProvider.get().eventBus.post(MenuToPlayEvent)
                }
            }
    }
}
