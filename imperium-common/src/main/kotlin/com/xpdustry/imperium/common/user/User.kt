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
package com.xpdustry.imperium.common.user

import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.common.snowflake.Snowflake
import java.net.InetAddress
import java.time.Instant

data class User(
    val snowflake: Snowflake,
    val uuid: MindustryUUID,
    val lastName: String,
    val lastAddress: InetAddress,
    val timesJoined: Int = 0,
    val lastJoin: Instant,
) {
    data class NamesAndAddresses(val names: Set<String>, val addresses: Set<InetAddress>) {
        companion object {
            val EMPTY = NamesAndAddresses(emptySet(), emptySet())
        }
    }

    enum class Setting(val default: Boolean, val description: String) {
        SHOW_WELCOME_MESSAGE(true, "Show the welcome message when joining the server."),
        RESOURCE_HUD(true, "Show a HUD with the evolution of the resources."),
        REMEMBER_LOGIN(true, "Remember the last used login."),
        DOUBLE_TAP_TILE_LOG(true, "Double tapping on a tile will display its history."),
        ANTI_BAN_EVADE(
            false, "Notifies you when a player leaves but rejoins with a different name."),
        CHAT_TRANSLATOR(true, "Translates chat messages based on the language of your game.")
    }
}
