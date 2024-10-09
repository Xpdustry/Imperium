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

import com.google.common.annotations.VisibleForTesting
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.hash.Argon2HashFunction
import com.xpdustry.imperium.common.hash.Argon2Params
import com.xpdustry.imperium.common.hash.GenericSaltyHashFunction
import com.xpdustry.imperium.common.hash.Hash
import com.xpdustry.imperium.common.hash.PBKDF2Params
import com.xpdustry.imperium.common.hash.ShaHashFunction
import com.xpdustry.imperium.common.hash.ShaType
import com.xpdustry.imperium.common.message.Message
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.exists
import com.xpdustry.imperium.common.security.DEFAULT_PASSWORD_REQUIREMENTS
import com.xpdustry.imperium.common.security.DEFAULT_USERNAME_REQUIREMENTS
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.security.PasswordRequirement
import com.xpdustry.imperium.common.security.UsernameRequirement
import com.xpdustry.imperium.common.security.findMissingPasswordRequirements
import com.xpdustry.imperium.common.security.findMissingUsernameRequirements
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert

interface AccountManager {

    suspend fun findByUsername(username: String): Account?

    suspend fun findById(id: Int): Account?

    suspend fun findByDiscord(discord: Long): Account?

    suspend fun findByIdentity(identity: Identity.Mindustry): Account?

    suspend fun existsByIdentity(identity: Identity.Mindustry): Boolean

    suspend fun existsById(id: Int): Boolean

    suspend fun updateDiscord(account: Int, discord: Long): AccountResult

    suspend fun register(username: String, password: CharArray): AccountResult

    suspend fun migrate(
        oldUsername: String,
        newUsername: String,
        password: CharArray
    ): AccountResult

    suspend fun changePassword(
        oldPassword: CharArray,
        newPassword: CharArray,
        identity: Identity.Mindustry
    ): AccountResult

    // TODO Move these in a account session manager
    suspend fun login(
        username: String,
        password: CharArray,
        identity: Identity.Mindustry
    ): AccountResult

    suspend fun refresh(identity: Identity.Mindustry): AccountResult

    suspend fun logout(identity: Identity.Mindustry, all: Boolean = false): AccountResult

    // TODO Move the following methods in a account stat manager
    suspend fun setAchievementCompletion(
        account: Int,
        achievement: Account.Achievement,
        completed: Boolean
    ): AccountResult

    suspend fun setAchievementProgression(
        account: Int,
        achievement: Account.Achievement,
        data: JsonObject,
    ): AccountResult

    suspend fun getAchievements(
        account: Int
    ): Map<Account.Achievement, Account.Achievement.Progression>

    suspend fun getAchievement(
        account: Int,
        achievement: Account.Achievement
    ): Account.Achievement.Progression

    suspend fun incrementGames(account: Int): Boolean

    suspend fun incrementPlaytime(account: Int, duration: Duration): Boolean

    suspend fun setRank(account: Int, rank: Rank)
}

@Serializable
data class AchievementCompletedMessage(val account: Int, val achievement: Account.Achievement) :
    Message

sealed interface AccountResult {

    data object Success : AccountResult

    data object AlreadyRegistered : AccountResult

    data object NotFound : AccountResult

    data object AlreadyLogged : AccountResult

    data object WrongPassword : AccountResult

    data class InvalidPassword(val missing: List<PasswordRequirement>) : AccountResult

    data class InvalidUsername(val missing: List<UsernameRequirement>) : AccountResult
}

class SimpleAccountManager(
    private val provider: SQLProvider,
    private val messenger: Messenger,
    private val config: ImperiumConfig
) : AccountManager, ImperiumApplication.Listener {

    override fun onImperiumInit() {
        provider.newTransaction {
            SchemaUtils.createMissingTablesAndColumns(
                AccountTable,
                AccountSessionTable,
                AccountAchievementTable,
                LegacyAccountTable,
                LegacyAccountAchievementTable)

            if (config.testing) {
                if (AccountTable.exists { AccountTable.username eq "test" }) {
                    return@newTransaction
                }
                val password = runBlocking {
                    GenericSaltyHashFunction.create("test".toCharArray(), PASSWORD_PARAMS)
                }
                AccountTable.insert {
                    it[username] = "test"
                    it[passwordHash] = password.hash
                    it[passwordSalt] = password.salt
                    it[rank] = Rank.OWNER
                }
                LOGGER.warn(
                    "Testing mode enabled, created test account, with credentials {}", "test:test")
            }
        }
    }

    override suspend fun findByUsername(username: String): Account? =
        provider.newSuspendTransaction {
            AccountTable.selectAll()
                .where { AccountTable.username eq username }
                .firstOrNull()
                ?.toAccount()
        }

    override suspend fun findById(id: Int): Account? =
        provider.newSuspendTransaction {
            AccountTable.selectAll().where { AccountTable.id eq id }.firstOrNull()?.toAccount()
        }

    override suspend fun findByDiscord(discord: Long): Account? =
        provider.newSuspendTransaction {
            AccountTable.selectAll()
                .where { AccountTable.discord eq discord }
                .firstOrNull()
                ?.toAccount()
        }

    override suspend fun findByIdentity(identity: Identity.Mindustry): Account? =
        provider.newSuspendTransaction {
            val sessionHash = createSessionHash(identity)
            (AccountTable leftJoin AccountSessionTable)
                .selectAll()
                .where {
                    (AccountSessionTable.hash eq sessionHash) and
                        (AccountSessionTable.expiration greater Instant.now())
                }
                .firstOrNull()
                ?.toAccount()
        }

    override suspend fun existsByIdentity(identity: Identity.Mindustry): Boolean =
        provider.newSuspendTransaction {
            val sessionHash = createSessionHash(identity)
            AccountSessionTable.exists {
                (AccountSessionTable.hash eq sessionHash) and
                    (AccountSessionTable.expiration less Instant.now())
            }
        }

    override suspend fun existsById(id: Int): Boolean =
        provider.newSuspendTransaction { AccountTable.exists { AccountTable.id eq id } }

    override suspend fun updateDiscord(account: Int, discord: Long): AccountResult =
        provider.newSuspendTransaction {
            val rows =
                AccountTable.update({ AccountTable.id eq account }) {
                    it[AccountTable.discord] = discord
                }
            if (rows == 0) AccountResult.NotFound else AccountResult.Success
        }

    override suspend fun register(username: String, password: CharArray): AccountResult =
        provider.newSuspendTransaction {
            if (AccountTable.exists { AccountTable.username eq username }) {
                return@newSuspendTransaction AccountResult.AlreadyRegistered
            }

            val hashUsr = ShaHashFunction.create(username.toCharArray(), ShaType.SHA256).hash
            if (LegacyAccountTable.exists { LegacyAccountTable.usernameHash eq hashUsr }) {
                return@newSuspendTransaction AccountResult.InvalidUsername(
                    listOf(UsernameRequirement.Reserved(username)))
            }

            val missingPwdRequirements =
                DEFAULT_PASSWORD_REQUIREMENTS.findMissingPasswordRequirements(password)
            if (missingPwdRequirements.isNotEmpty()) {
                return@newSuspendTransaction AccountResult.InvalidPassword(missingPwdRequirements)
            }

            val missingUsrRequirements =
                DEFAULT_USERNAME_REQUIREMENTS.findMissingUsernameRequirements(username)
            if (missingUsrRequirements.isNotEmpty()) {
                return@newSuspendTransaction AccountResult.InvalidUsername(missingUsrRequirements)
            }

            val hashPwd = GenericSaltyHashFunction.create(password, PASSWORD_PARAMS)
            AccountTable.insert {
                it[AccountTable.username] = username
                it[passwordHash] = hashPwd.hash
                it[passwordSalt] = hashPwd.salt
            }

            return@newSuspendTransaction AccountResult.Success
        }

    override suspend fun migrate(
        oldUsername: String,
        newUsername: String,
        password: CharArray
    ): AccountResult =
        provider.newSuspendTransaction {
            val oldUsernameHash =
                ShaHashFunction.create(oldUsername.lowercase().toCharArray(), ShaType.SHA256).hash

            val oldAccount =
                LegacyAccountTable.selectAll()
                    .where { LegacyAccountTable.usernameHash eq oldUsernameHash }
                    .firstOrNull() ?: return@newSuspendTransaction AccountResult.NotFound

            if (!GenericSaltyHashFunction.equals(
                password,
                Hash(
                    oldAccount[LegacyAccountTable.passwordHash],
                    oldAccount[LegacyAccountTable.passwordSalt],
                    LEGACY_PASSWORD_PARAMS))) {
                return@newSuspendTransaction AccountResult.NotFound
            }

            if (AccountTable.exists { AccountTable.username eq newUsername }) {
                return@newSuspendTransaction AccountResult.AlreadyRegistered
            }

            val missing = DEFAULT_USERNAME_REQUIREMENTS.findMissingUsernameRequirements(newUsername)
            if (missing.isNotEmpty()) {
                return@newSuspendTransaction AccountResult.InvalidUsername(missing)
            }

            val newPasswordHash = GenericSaltyHashFunction.create(password, PASSWORD_PARAMS)
            val newAccount =
                AccountTable.insertAndGetId {
                    it[username] = newUsername
                    it[passwordHash] = newPasswordHash.hash
                    it[passwordSalt] = newPasswordHash.salt
                    it[playtime] = oldAccount[LegacyAccountTable.playtime]
                    it[games] = oldAccount[LegacyAccountTable.games]
                    it[legacy] = true
                    it[rank] = oldAccount[LegacyAccountTable.rank]
                }

            val oldAchievements =
                LegacyAccountAchievementTable.selectAll().where {
                    LegacyAccountAchievementTable.account eq oldAccount[LegacyAccountTable.id]
                }

            AccountAchievementTable.batchInsert(oldAchievements) {
                this[AccountAchievementTable.account] = newAccount
                this[AccountAchievementTable.achievement] =
                    it[LegacyAccountAchievementTable.achievement]
                this[AccountAchievementTable.completed] = true
            }

            LegacyAccountTable.deleteWhere { id eq oldAccount[LegacyAccountTable.id] }

            return@newSuspendTransaction AccountResult.Success
        }

    override suspend fun changePassword(
        oldPassword: CharArray,
        newPassword: CharArray,
        identity: Identity.Mindustry
    ): AccountResult =
        provider.newSuspendTransaction {
            val sessionHash = createSessionHash(identity)
            val account =
                (AccountTable leftJoin AccountSessionTable)
                    .select(AccountTable.id, AccountTable.passwordHash, AccountTable.passwordSalt)
                    .where { AccountSessionTable.hash eq sessionHash }
                    .firstOrNull() ?: return@newSuspendTransaction AccountResult.NotFound

            if (!GenericSaltyHashFunction.equals(
                oldPassword,
                Hash(
                    account[AccountTable.passwordHash],
                    account[AccountTable.passwordSalt],
                    PASSWORD_PARAMS))) {
                return@newSuspendTransaction AccountResult.WrongPassword
            }

            val missing = DEFAULT_PASSWORD_REQUIREMENTS.findMissingPasswordRequirements(newPassword)
            if (missing.isNotEmpty()) {
                return@newSuspendTransaction AccountResult.InvalidPassword(missing)
            }

            val newPasswordHash = GenericSaltyHashFunction.create(newPassword, PASSWORD_PARAMS)
            AccountTable.update({ AccountTable.id eq account[AccountTable.id] }) {
                it[passwordHash] = newPasswordHash.hash
                it[passwordSalt] = newPasswordHash.salt
            }

            return@newSuspendTransaction AccountResult.Success
        }

    override suspend fun login(
        username: String,
        password: CharArray,
        identity: Identity.Mindustry
    ): AccountResult =
        provider.newSuspendTransaction {
            val result =
                AccountTable.select(
                        AccountTable.id, AccountTable.passwordHash, AccountTable.passwordSalt)
                    .where { AccountTable.username eq username }
                    .firstOrNull() ?: return@newSuspendTransaction AccountResult.NotFound

            if (!GenericSaltyHashFunction.equals(
                password,
                Hash(
                    result[AccountTable.passwordHash],
                    result[AccountTable.passwordSalt],
                    PASSWORD_PARAMS))) {
                return@newSuspendTransaction AccountResult.NotFound
            }

            val sessionHash = createSessionHash(identity)
            if (AccountSessionTable.exists { AccountSessionTable.hash eq sessionHash }) {
                return@newSuspendTransaction AccountResult.AlreadyLogged
            }

            AccountSessionTable.insert {
                it[account] = result[AccountTable.id]
                it[hash] = sessionHash
                it[expiration] = Instant.now().plus(SESSION_TOKEN_DURATION.toJavaDuration())
            }

            return@newSuspendTransaction AccountResult.Success
        }

    override suspend fun refresh(identity: Identity.Mindustry): AccountResult =
        provider.newSuspendTransaction {
            val sessionHash = createSessionHash(identity)

            AccountSessionTable.deleteWhere {
                (hash eq sessionHash) and (expiration less Instant.now())
            }

            val updated =
                AccountSessionTable.update({ AccountSessionTable.hash eq sessionHash }) {
                    it[expiration] = Instant.now().plus(SESSION_TOKEN_DURATION.toJavaDuration())
                }

            if (updated == 0) {
                AccountResult.NotFound
            } else {
                AccountResult.Success
            }
        }

    override suspend fun logout(identity: Identity.Mindustry, all: Boolean): AccountResult =
        provider.newSuspendTransaction {
            val sessionHash = createSessionHash(identity)
            val session =
                AccountSessionTable.select(AccountSessionTable.account)
                    .where { AccountSessionTable.hash eq sessionHash }
                    .firstOrNull() ?: return@newSuspendTransaction AccountResult.NotFound

            if (all) {
                AccountSessionTable.deleteWhere { account eq session[account] }
            } else {
                AccountSessionTable.deleteWhere { hash eq sessionHash }
            }

            AccountResult.Success
        }

    override suspend fun setAchievementCompletion(
        account: Int,
        achievement: Account.Achievement,
        completed: Boolean
    ): AccountResult {
        if (!existsById(account)) {
            return AccountResult.NotFound
        }

        val wasCompleted =
            provider.newSuspendTransaction {
                AccountAchievementTable.select(AccountAchievementTable.completed)
                    .where {
                        (AccountAchievementTable.account eq account) and
                            (AccountAchievementTable.achievement eq achievement)
                    }
                    .firstOrNull()
                    ?.get(AccountAchievementTable.completed) ?: false
            }

        provider.newSuspendTransaction {
            AccountAchievementTable.upsert {
                it[AccountAchievementTable.account] = account
                it[AccountAchievementTable.achievement] = achievement
                it[AccountAchievementTable.completed] = completed
            }
        }

        if (!wasCompleted && completed) {
            messenger.publish(AchievementCompletedMessage(account, achievement), local = true)
        }

        return AccountResult.Success
    }

    override suspend fun setAchievementProgression(
        account: Int,
        achievement: Account.Achievement,
        data: JsonObject
    ): AccountResult {
        if (!existsById(account)) {
            return AccountResult.NotFound
        }

        provider.newSuspendTransaction {
            AccountAchievementTable.upsert {
                it[AccountAchievementTable.account] = account
                it[AccountAchievementTable.achievement] = achievement
                it[AccountAchievementTable.data] = data
            }
        }

        return AccountResult.Success
    }

    override suspend fun getAchievements(
        account: Int
    ): Map<Account.Achievement, Account.Achievement.Progression> =
        provider.newSuspendTransaction {
            AccountAchievementTable.select(
                    AccountAchievementTable.achievement,
                    AccountAchievementTable.data,
                    AccountAchievementTable.completed)
                .where { AccountAchievementTable.account eq account }
                .associate {
                    it[AccountAchievementTable.achievement] to it.toAchievementProgression()
                }
        }

    override suspend fun getAchievement(
        account: Int,
        achievement: Account.Achievement
    ): Account.Achievement.Progression =
        provider.newSuspendTransaction {
            AccountAchievementTable.select(
                    AccountAchievementTable.data, AccountAchievementTable.completed)
                .where {
                    (AccountAchievementTable.account eq account) and
                        (AccountAchievementTable.achievement eq achievement)
                }
                .firstOrNull()
                ?.toAchievementProgression() ?: Account.Achievement.Progression.ZERO
        }

    override suspend fun incrementGames(account: Int): Boolean =
        provider.newSuspendTransaction {
            AccountTable.update({ AccountTable.id eq account }) { it[games] = games.plus(1) } != 0
        }

    override suspend fun incrementPlaytime(account: Int, duration: Duration): Boolean =
        provider.newSuspendTransaction {
            AccountTable.update({ AccountTable.id eq account }) {
                it[playtime] = playtime.plus(duration.toJavaDuration())
            } != 0
        }

    override suspend fun setRank(account: Int, rank: Rank) {
        val changed =
            provider.newSuspendTransaction {
                val current =
                    AccountTable.select(AccountTable.rank)
                        .where { AccountTable.id eq account }
                        .firstOrNull()
                        ?.get(AccountTable.rank)
                if (current == rank) {
                    return@newSuspendTransaction false
                }
                AccountTable.update({ AccountTable.id eq account }) {
                    it[AccountTable.rank] = rank
                } != 0
            }
        if (changed) {
            messenger.publish(RankChangeEvent(account), local = true)
        }
    }

    @VisibleForTesting
    internal suspend fun createSessionHash(identity: Identity.Mindustry): ByteArray =
        Argon2HashFunction.create(
                identity.uuid.toCharArray(), SESSION_TOKEN_PARAMS, identity.usid.toByteArray())
            .hash

    private fun ResultRow.toAccount() =
        Account(
            id = this[AccountTable.id].value,
            username = this[AccountTable.username],
            discord = this[AccountTable.discord],
            games = this[AccountTable.games],
            playtime = this[AccountTable.playtime].toKotlinDuration(),
            creation = this[AccountTable.creation],
            legacy = this[AccountTable.legacy],
            rank = this[AccountTable.rank])

    private fun ResultRow.toAchievementProgression() =
        Account.Achievement.Progression(
            data = this[AccountAchievementTable.data],
            completed = this[AccountAchievementTable.completed])

    companion object {
        private val LOGGER by LoggerDelegate()

        private val SESSION_TOKEN_DURATION = 7.days

        private val SESSION_TOKEN_PARAMS =
            Argon2Params(
                memory = 19,
                iterations = 2,
                length = 32,
                saltLength = 8,
                parallelism = 8,
                type = Argon2Params.Type.ID,
                version = Argon2Params.Version.V13,
            )

        private val PASSWORD_PARAMS =
            Argon2Params(
                memory = 64 * 1024,
                iterations = 3,
                parallelism = 2,
                length = 64,
                type = Argon2Params.Type.ID,
                version = Argon2Params.Version.V13,
                saltLength = 64,
            )

        internal val LEGACY_PASSWORD_PARAMS =
            PBKDF2Params(
                hmac = PBKDF2Params.Hmac.SHA256,
                iterations = 10000,
                length = 256,
                saltLength = 16,
            )
    }
}
