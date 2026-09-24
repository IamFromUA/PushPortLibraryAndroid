// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

import dev.pushport.sdk.internal.core.SessionTracker
import dev.pushport.sdk.internal.core.SyncOutcome
import dev.pushport.sdk.internal.core.UserController
import dev.pushport.sdk.internal.model.UserOperation
import dev.pushport.sdk.internal.ports.HttpFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class UserAndUsageTest {
    @Test fun `foreground sessions distinguish short pauses from new visits and use monotonic time`() {
        val f = CoreFixture()
        var wall = 100000L
        var elapsed = 1000L
        val tracker = SessionTracker(f.repository, { wall }, { elapsed })
        f.controller.sync()
        assertEquals(
            0,
            f.repository
                .read()
                .usage.sessionCount,
        )
        tracker.foreground()
        tracker.foreground()
        elapsed += 5000
        wall += 100000 // Changing the device clock does not add usage time.
        tracker.checkpoint()
        tracker.background()
        assertEquals(
            1,
            f.repository
                .read()
                .usage.sessionCount,
        )
        assertEquals(
            5000,
            f.repository
                .read()
                .usage.totalUsageMillis,
        )
        wall += 1000
        elapsed += 1000
        tracker.foreground()
        elapsed += 2000
        tracker.background()
        assertEquals(
            1,
            f.repository
                .read()
                .usage.sessionCount,
        )
        assertEquals(
            7000,
            f.repository
                .read()
                .usage.totalUsageMillis,
        )
        wall += 30000
        SessionTracker(f.repository, { wall }, { elapsed }).foreground()
        assertEquals(
            2,
            f.repository
                .read()
                .usage.sessionCount,
        )
        assertEquals(
            100000L,
            f.repository
                .read()
                .usage.firstSessionAt,
        )
        assertEquals(
            wall,
            f.repository
                .read()
                .usage.lastSessionAt,
        )
    }

    @Test fun `consent gate stops snapshots calls profiles and session tracking`() =
        runBlocking {
            val f = CoreFixture()
            f.collector.capture()
            val before = f.repository.read().snapshot
            f.repository.update { it.copy(consentRequired = true, consentGiven = false) }
            f.api.failure = AssertionError("No API calls are allowed").let { IllegalStateException(it) }
            assertNull(f.collector.capture())
            assertEquals(SyncOutcome.SUCCESS, f.coordinator().synchronize(0))
            SessionTracker(f.repository, { 10000 }, { 10000 }).foreground()
            assertEquals(
                0,
                f.repository
                    .read()
                    .usage.sessionCount,
            )
            assertEquals(before, f.repository.read().snapshot)
            assertThrows(
                IllegalStateException::class.java,
            ) { UserController(f.repository, f.scheduler).enqueue(UserOperation(kind = "tags")) }
            assertTrue(f.api.registrations.isEmpty())
        }

    @Test fun `retries preserve operations and account switches discard old pending data`() =
        runBlocking {
            val f = CoreFixture()
            val users = UserController(f.repository, f.scheduler)
            users.enqueue(UserOperation(kind = "event", eventName = "purchase"))
            var applied = 0L
            var effects = 0
            var loseResponse = true
            f.api.onUser = { op ->
                if (op.revision > applied) {
                    applied = op.revision
                    effects++
                }
                if (loseResponse) {
                    loseResponse = false
                    throw IOException("response lost")
                }
                PushPortUser("server-user", null, emptyMap(), null, null, null, null, applied)
            }
            assertEquals(SyncOutcome.RETRY, f.coordinator().synchronize(0))
            assertEquals(
                1,
                f.repository
                    .read()
                    .pendingUserOperations.size,
            )
            assertEquals(SyncOutcome.SUCCESS, f.coordinator().synchronize(1))
            assertEquals(1, effects)
            assertTrue(
                f.repository
                    .read()
                    .pendingUserOperations
                    .isEmpty(),
            )
            assertEquals("server-user", users.profile()!!.userId)
            users.enqueue(UserOperation(kind = "tags", tags = mapOf("old-account" to "value")))
            users.enqueue(UserOperation(kind = "login", externalId = "new-account", identityToken = "a".repeat(43)))
            users.enqueue(UserOperation(kind = "tags", tags = mapOf("new-account" to "value")))
            assertEquals(
                listOf("login", "tags"),
                f.repository
                    .read()
                    .pendingUserOperations
                    .map { it.kind },
            )
            f.api.onUser = { throw HttpFailure(401) }
            assertEquals(SyncOutcome.FAILURE, f.coordinator().synchronize(0))
            assertEquals(
                2,
                f.repository
                    .read()
                    .pendingUserOperations.size,
            ) // Never apply tags to previous account after failed login.
            users.enqueue(UserOperation(kind = "logout"))
            assertEquals(
                listOf("logout"),
                f.repository
                    .read()
                    .pendingUserOperations
                    .map { it.kind },
            )
            assertEquals(
                5,
                f.repository
                    .read()
                    .pendingUserOperations
                    .single()
                    .revision,
            )
        }

    @Test fun `server rejected metadata reports failure but cannot poison later operations`() =
        runBlocking {
            val f = CoreFixture()
            val users = UserController(f.repository, f.scheduler)
            users.enqueue(UserOperation(kind = "tags", tags = mapOf("extra-tag" to "over-server-limit")))
            users.enqueue(UserOperation(kind = "event", eventName = "view"))
            f.api.onUser = { throw HttpFailure(400) }
            assertEquals(SyncOutcome.FAILURE, f.coordinator().synchronize(0))
            assertEquals("HTTP 400", f.repository.read().syncError)
            assertEquals(
                listOf("event"),
                f.repository
                    .read()
                    .pendingUserOperations
                    .map { it.kind },
            )
            f.api.onUser = { PushPortUser("user", null, emptyMap(), null, null, null, null, it.revision) }
            assertEquals(SyncOutcome.SUCCESS, f.coordinator().synchronize(0))
            assertTrue(
                f.repository
                    .read()
                    .pendingUserOperations
                    .isEmpty(),
            )
        }

    @Test fun `optional data is validated bounded copied and redacted before persistence`() {
        val f = CoreFixture()
        val users = UserController(f.repository, f.scheduler)
        for (op in listOf(
            UserOperation(kind = "login", externalId = "trailing ", identityToken = "a".repeat(43)),
            UserOperation(kind = "phone", phoneNumber = "123"),
            UserOperation(kind = "email", email = "bad"),
            UserOperation(kind = "location", latitude = 91.0, longitude = 0.0),
            UserOperation(kind = "location", latitude = 1.0),
            UserOperation(kind = "event", eventName = "bad name"),
        )) {
            assertThrows(IllegalArgumentException::class.java) { users.enqueue(op) }
        }
        val tags = mutableMapOf<String, String?>("tier" to "pro")
        users.enqueue(UserOperation(kind = "tags", tags = tags))
        tags["tier"] = "changed"
        assertEquals(
            "pro",
            f.repository
                .read()
                .pendingUserOperations
                .single()
                .tags["tier"],
        )
        repeat(99) { users.enqueue(UserOperation(kind = "event", eventName = "view")) }
        assertThrows(IllegalStateException::class.java) { users.enqueue(UserOperation(kind = "event", eventName = "view")) }
        assertFalse(UserOperation(kind = "email", email = "private@example.test").toString().contains("private"))
        users.enqueue(UserOperation(kind = "logout"))
        assertEquals(
            1,
            f.repository
                .read()
                .pendingUserOperations.size,
        )
    }
}
