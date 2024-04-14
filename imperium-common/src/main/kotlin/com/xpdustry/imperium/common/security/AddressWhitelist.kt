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
package com.xpdustry.imperium.common.security

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.misc.exists
import java.net.InetAddress
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore

interface AddressWhitelist {
    suspend fun addAddress(address: InetAddress)

    suspend fun containsAddress(address: InetAddress): Boolean

    suspend fun removeAddress(address: InetAddress)
}

class SimpleAddressWhitelist(private val provider: SQLProvider) :
    AddressWhitelist, ImperiumApplication.Listener {

    override fun onImperiumInit() {
        provider.newTransaction { SchemaUtils.create(AddressWhitelistTable) }
    }

    override suspend fun addAddress(address: InetAddress): Unit =
        provider.newSuspendTransaction {
            AddressWhitelistTable.insertIgnore {
                it[AddressWhitelistTable.address] = address.address
            }
        }

    override suspend fun containsAddress(address: InetAddress) =
        provider.newSuspendTransaction {
            AddressWhitelistTable.exists { AddressWhitelistTable.address eq address.address }
        }

    override suspend fun removeAddress(address: InetAddress): Unit =
        provider.newSuspendTransaction {
            AddressWhitelistTable.deleteWhere { AddressWhitelistTable.address eq address.address }
        }
}
