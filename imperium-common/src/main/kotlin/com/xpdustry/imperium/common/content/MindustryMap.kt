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
package com.xpdustry.imperium.common.content

import com.xpdustry.imperium.common.snowflake.Snowflake
import java.time.Instant
import kotlin.time.Duration

data class MindustryMap(
    val snowflake: Snowflake,
    val name: String,
    val description: String?,
    val author: String?,
    val width: Int,
    val height: Int,
    val lastUpdate: Instant,
    val gamemodes: Set<MindustryGamemode>
) {
    enum class Difficulty {
        EASY,
        NORMAL,
        HARD,
        EXPERT
    }

    data class Rating(val user: Snowflake, val score: Int, val difficulty: Difficulty)

    data class Stats(
        val score: Double,
        val difficulty: Difficulty,
        val games: Int,
        val playtime: Duration,
        val record: Snowflake?
    )

    // TODO
    //   Add metadata instead of "winner" for special gamemodes
    //   Such as PVP where I can add a MVP, or hexed with the winner, etc...
    data class Game(
        val snowflake: Snowflake,
        val map: Snowflake,
        val server: String,
        val start: Instant,
        val playtime: Duration,
        val unitsCreated: Int,
        val ennemiesKilled: Int,
        val wavesLasted: Int,
        val buildingsConstructed: Int,
        val buildingsDeconstructed: Int,
        val buildingsDestroyed: Int,
        val winner: UByte
    )
}
