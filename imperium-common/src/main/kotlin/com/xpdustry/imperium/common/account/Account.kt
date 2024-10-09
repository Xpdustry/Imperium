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
package com.xpdustry.imperium.common.account

import java.time.Instant
import kotlin.time.Duration
import kotlinx.serialization.json.JsonObject

data class Account(
    val id: Int,
    val username: String,
    val discord: Long?,
    val games: Int,
    val playtime: Duration,
    val creation: Instant,
    val legacy: Boolean,
    val rank: Rank,
) {
    enum class Achievement(val secret: Boolean = false) {
        ACTIVE(true),
        HYPER(true),
        GAMER,
        DAY,
        WEEK,
        MONTH;

        data class Progression(val data: JsonObject, var completed: Boolean = false) {
            companion object {
                val ZERO = Progression(JsonObject(emptyMap()))
            }
        }
    }
}
