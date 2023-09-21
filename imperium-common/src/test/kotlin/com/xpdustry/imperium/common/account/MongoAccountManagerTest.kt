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

import com.xpdustry.imperium.common.application.SimpleImperiumApplication
import com.xpdustry.imperium.common.config.DatabaseConfig
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.inject.module
import com.xpdustry.imperium.common.inject.single
import com.xpdustry.imperium.common.misc.ExitStatus
import com.xpdustry.imperium.common.security.Identity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.InetAddress
import java.util.Base64
import kotlin.random.Random

// TODO: Finish writing tests
@Testcontainers
class MongoAccountManagerTest {
    private lateinit var application: SimpleImperiumApplication
    private lateinit var manager: MongoAccountManager

    @BeforeEach
    fun init() {
        application = SimpleImperiumApplication(createModule())
        manager = application.instances.get<AccountManager>() as MongoAccountManager
        application.init()
    }

    @AfterEach
    fun exit() {
        application.exit(ExitStatus.EXIT)
    }

    @Test
    fun `test simple registration`() = runTest {
        val username = randomUsername()
        val identity = randomPlayerIdentity()

        Assertions.assertInstanceOf(
            AccountOperationResult.InvalidPassword::class.java,
            manager.register(username, INVALID_PASSWORD, identity),
        )

        Assertions.assertEquals(
            AccountOperationResult.Success,
            manager.register(username, TEST_PASSWORD_1, identity),
        )

        Assertions.assertEquals(
            AccountOperationResult.AlreadyRegistered,
            manager.register(username, TEST_PASSWORD_1, identity),
        )

        val account = manager.findByUsername(username)
        Assertions.assertNotNull(account)
        Assertions.assertEquals(username, account!!.username)
    }

    @Test
    fun `test simple login`() = runTest {
        val username = randomUsername()
        val identity = randomPlayerIdentity()

        Assertions.assertEquals(
            AccountOperationResult.NotRegistered,
            manager.login(username, TEST_PASSWORD_1, identity),
        )

        manager.register(username, TEST_PASSWORD_1, identity)

        Assertions.assertEquals(
            AccountOperationResult.WrongPassword,
            manager.login(username, TEST_PASSWORD_2, identity),
        )

        manager.login(username, TEST_PASSWORD_1, identity)

        val account = manager.findByUsername(username)
        Assertions.assertNotNull(account)
        Assertions.assertTrue(account!!.sessions.contains(manager.createSessionToken(identity)))
    }

    private fun randomPlayerIdentity(): Identity.Mindustry {
        val uuidBytes = ByteArray(16)
        Random.nextBytes(uuidBytes)
        val usidBytes = ByteArray(8)
        Random.nextBytes(usidBytes)
        return Identity.Mindustry(
            Random.nextLong().toString(),
            Base64.getEncoder().encodeToString(uuidBytes),
            Base64.getEncoder().encodeToString(usidBytes),
            InetAddress.getLoopbackAddress(),
        )
    }

    private fun randomUsername(): String {
        val chars = CharArray(16)
        for (i in chars.indices) {
            chars[i] = Random.nextInt('a'.code, 'z'.code).toChar()
        }
        return String(chars)
    }

    private fun createModule() = module("account-test") {
        include(com.xpdustry.imperium.common.commonModule())
        single<ImperiumConfig> {
            ImperiumConfig(database = DatabaseConfig.Mongo(port = MONGO_CONTAINER.firstMappedPort))
        }
    }

    companion object {
        @Container
        private val MONGO_CONTAINER = MongoDBContainer(DockerImageName.parse("mongo:6"))

        private val TEST_PASSWORD_1 = "ABc123!#".toCharArray()
        private val TEST_PASSWORD_2 = "123ABc!#".toCharArray()
        private val INVALID_PASSWORD = "1234".toCharArray()
    }
}
