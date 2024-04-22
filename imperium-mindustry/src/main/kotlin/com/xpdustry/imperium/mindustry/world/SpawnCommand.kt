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
package com.xpdustry.imperium.mindustry.world

import arc.math.geom.Vec2
import com.xpdustry.distributor.command.CommandSender
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.Flag
import kotlin.random.Random
import mindustry.Vars
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.type.UnitType
import org.incendo.cloud.annotation.specifier.Range

class SpawnCommand : ImperiumApplication.Listener {

    @ImperiumCommand(["spawn"], Rank.ADMIN)
    @ClientSide
    fun onUnitSpawnCommand(
        sender: CommandSender,
        unit: UnitType,
        @Range(min = "1", max = "300") count: Int = 1,
        team: Team = sender.player.team(),
        @Flag x: Int = sender.player.tileX(),
        @Flag y: Int = sender.player.tileY(),
        @Flag silent: Boolean,
    ) {
        var spawned = 0
        repeat(count) {
            val worldX = (x * Vars.tilesize) + Random.nextInt(Vars.tilesize * 2)
            val worldY = (y * Vars.tilesize) + Random.nextInt(Vars.tilesize * 2)
            val entity = unit.spawn(Vec2(worldX.toFloat(), worldY.toFloat()), team)
            if (!entity.dead()) spawned++
        }
        if (spawned == 0) {
            sender.sendWarning(
                "You reached the unit cap of ${unit.name} for the team ${team.coloredName()}.")
            return
        }
        if (silent) {
            sender.sendMessage(
                "You spawned $spawned ${unit.name} for team ${team.coloredName()} at ($x, $y).")
        } else {
            Call.sendMessage(
                "${sender.name}[white] spawned $spawned ${unit.name} for team ${team.coloredName()} at ($x, $y).")
        }
    }
}
