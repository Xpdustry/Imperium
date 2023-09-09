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
package com.xpdustry.imperium.common.account

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.mongo.MongoEntityCollection
import com.xpdustry.imperium.common.mongo.MongoProvider

internal class MongoUserManager(private val mongo: MongoProvider) : UserManager, ImperiumApplication.Listener {

    private lateinit var users: MongoEntityCollection<User, String>

    override fun onImperiumInit() {
        users = mongo.getCollection("users", User::class)
    }

    override suspend fun findByUuidOrCreate(uuid: MindustryUUID): User = users.findById(uuid) ?: User(uuid)

    override suspend fun updateOrCreateByUuid(uuid: MindustryUUID, updater: suspend (User) -> Unit) {
        val user = users.findById(uuid) ?: User(uuid)
        updater(user)
        users.save(user)
    }
}
