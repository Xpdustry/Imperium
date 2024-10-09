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

import java.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.duration
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.json

object AccountTable : IntIdTable("account") {
    val username = varchar("username", 32).uniqueIndex()
    val passwordHash = binary("password_hash", 64)
    val passwordSalt = binary("password_salt", 64)
    val discord = long("discord").nullable().default(null)
    val games = integer("games").default(0)
    val playtime = duration("playtime").default(Duration.ZERO)
    val legacy = bool("legacy").default(false)
    val rank = enumerationByName<Rank>("rank", 32).default(Rank.EVERYONE)
    val creation = timestamp("creation").defaultExpression(CurrentTimestamp)
}

object AccountSessionTable : Table("account_user_session") {
    val account = reference("account_id", AccountTable, onDelete = ReferenceOption.CASCADE)
    val hash = binary("hash", 64).uniqueIndex()
    val expiration = timestamp("expiration")
    override val primaryKey = PrimaryKey(account, hash)
}

object AccountAchievementTable : Table("account_achievement") {
    val account = reference("account_id", AccountTable, onDelete = ReferenceOption.CASCADE)
    val achievement = enumerationByName<Account.Achievement>("achievement", 32)
    // TODO
    //   Provides a new instance of JsonObject clientside,
    //   but the default is not set database-side
    //   invest in key-val table for account dynamic data
    val data = json("data", Json, JsonObject.serializer()).clientDefault { JsonObject(emptyMap()) }
    val completed = bool("completed").default(false)
    override val primaryKey = PrimaryKey(account, achievement)
}

object LegacyAccountTable : IntIdTable("legacy_account") {
    val usernameHash = binary("username_hash", 32).uniqueIndex()
    val passwordHash = binary("password_hash", 32)
    val passwordSalt = binary("password_salt", 16)
    val games = integer("games").default(0)
    val playtime = duration("playtime").default(Duration.ZERO)
    val rank = enumerationByName<Rank>("rank", 32)
}

object LegacyAccountAchievementTable : Table("legacy_account_achievement") {
    val account =
        reference("legacy_account_id", LegacyAccountTable, onDelete = ReferenceOption.CASCADE)
    val achievement = enumerationByName<Account.Achievement>("achievement", 32)
    override val primaryKey = PrimaryKey(account, achievement)
}
