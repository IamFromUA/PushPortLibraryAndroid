// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

import dev.pushport.sdk.internal.core.SyncOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class TokenRecoveryTest {
    @Test fun `temporary token failure registers metadata and recovers on a bounded retry`() =
        runTest {
            val fixture = CoreFixture()
            fixture.api.config = TEST_FIREBASE
            fixture.provider.fetch = { throw IOException("synthetic token failure") }
            val sync = fixture.coordinator()
            assertEquals(SyncOutcome.RETRY, sync.synchronize(0))
            assertNull(
                fixture.api.registrations
                    .single()
                    .fcmToken,
            )
            assertNotNull(fixture.repository.read().lastSyncedAt)
            assertNotNull(fixture.repository.read().pushError)
            fixture.provider.fetch = { "recovered-token" }
            assertEquals(SyncOutcome.SUCCESS, sync.synchronize(1))
            assertEquals(
                "recovered-token",
                fixture.api.registrations
                    .last()
                    .fcmToken,
            )
            assertNull(fixture.repository.read().pushError)
            assertEquals(TEST_IDENTITY, fixture.repository.read().identity)
        }

    @Test fun `token timeout uses virtual time and stops retrying at the limit`() =
        runTest {
            val fixture = CoreFixture()
            fixture.api.config = TEST_FIREBASE
            fixture.provider.fetch = {
                delay(100)
                "too-late"
            }
            val sync = fixture.coordinator(10)
            assertEquals(SyncOutcome.RETRY, sync.synchronize(0))
            assertEquals(SyncOutcome.FAILURE, sync.synchronize(8))
            assertEquals(1, fixture.api.registrations.size)
        }

    @Test fun `changed Firebase configuration never reuses the previous project's token`() =
        runTest {
            val fixture = CoreFixture()
            fixture.api.config = TEST_FIREBASE
            val sync = fixture.coordinator()
            sync.synchronize(0)
            fixture.api.config = TEST_FIREBASE.copy(projectId = "another-project", senderId = "456")
            fixture.provider.fetch = { throw IOException("unavailable") }
            assertEquals(SyncOutcome.RETRY, sync.synchronize(0))
            assertNull(fixture.repository.read().fcmToken)
            assertNull(
                fixture.api.registrations
                    .last()
                    .fcmToken,
            )
        }

    @Test fun `invalid Firebase settings report failure without losing queued metadata`() =
        runTest {
            val fixture = CoreFixture()
            fixture.api.config = TEST_FIREBASE
            fixture.provider.fetch = { throw IllegalArgumentException("bad configuration") }
            assertEquals(SyncOutcome.FAILURE, fixture.coordinator().synchronize(0))
            assertNotNull(fixture.repository.read().lastSyncedAt)
            assertNotNull(fixture.repository.read().pushError)
            assertNull(fixture.repository.read().syncError)
        }

    @Test fun `temporary refresh error keeps a token from the same Firebase project`() =
        runTest {
            val fixture = CoreFixture()
            fixture.api.config = TEST_FIREBASE
            val sync = fixture.coordinator()
            sync.synchronize(0)
            fixture.provider.fetch = { throw IOException("unavailable") }
            assertEquals(SyncOutcome.RETRY, sync.synchronize(0))
            assertEquals("test-fcm-token", fixture.repository.read().fcmToken)
            assertEquals(1, fixture.api.registrations.size)
        }
}
