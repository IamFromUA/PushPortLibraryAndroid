// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

import dev.pushport.sdk.internal.core.SyncOutcome
import dev.pushport.sdk.internal.ports.HttpFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class SyncCoordinatorTest {
    @Test fun `locale and token updates retain identity and unchanged snapshots retain their revision`() {
        val fixture = CoreFixture()
        val original = fixture.collector.capture()!!
        assertEquals(original, fixture.collector.capture())
        fixture.repository.update { it.copy(fcmToken = "rotated-token") }
        val updated = fixture.collector.capture(listOf("de-DE"))!!
        assertEquals("de", updated.language)
        assertEquals("rotated-token", updated.fcmToken)
        assertTrue(updated.revision > original.revision)
        fixture.controller.initialize(PushPortConfig(TEST_APP_ID, "https://example.test/"))
        assertEquals(TEST_IDENTITY, fixture.repository.read().identity)
    }

    @Test fun `initialization cannot rebind an existing installation`() {
        val fixture = CoreFixture()
        val before = fixture.repository.read()
        assertThrows(IllegalArgumentException::class.java) {
            fixture.controller.initialize(PushPortConfig(TEST_APP_ID, "https://another.test"))
        }
        assertEquals(before, fixture.repository.read())
    }

    @Test fun `unsafe endpoints are rejected and local HTTP requires a debug application`() {
        for (url in listOf("http://example.com", "https://user:secret@example.com", "https://example.com/?q=1")) {
            assertThrows(IllegalArgumentException::class.java) { PushPortConfig(TEST_APP_ID, url, true).validate(true) }
        }
        assertThrows(IllegalArgumentException::class.java) { PushPortConfig(TEST_APP_ID, "http://127.0.0.1", true).validate(false) }
        assertEquals("http://10.0.2.2:8080", PushPortConfig(TEST_APP_ID, "http://10.0.2.2:8080/", true).validate(true).serverUrl)
    }

    @Test fun `registration without Firebase succeeds and unchanged snapshots wait for the heartbeat`() =
        runBlocking {
            val fixture = CoreFixture()
            val sync = fixture.coordinator()
            assertEquals(SyncOutcome.SUCCESS, sync.synchronize(0))
            assertNull(
                fixture.api.registrations
                    .single()
                    .fcmToken,
            )
            assertNotNull(fixture.repository.read().lastSyncedAt)
            assertNotNull(fixture.repository.read().pushError)
            sync.synchronize(0)
            assertEquals(1, fixture.api.registrations.size)
            fixture.now += TimeUnit.HOURS.toMillis(12)
            sync.synchronize(0)
            assertEquals(2, fixture.api.registrations.size)
        }

    @Test fun `rotated Firebase token is uploaded with the same installation identity`() =
        runBlocking {
            val fixture = CoreFixture()
            fixture.api.config = TEST_FIREBASE
            val sync = fixture.coordinator()
            sync.synchronize(0)
            fixture.provider.fetch = { "new-token" }
            sync.synchronize(0)
            assertEquals(listOf("test-fcm-token", "new-token"), fixture.api.registrations.map { it.fcmToken })
            assertEquals(TEST_IDENTITY, fixture.repository.read().identity)
        }

    @Test fun `a change during upload remains dirty and schedules the next synchronization`() =
        runBlocking {
            val fixture = CoreFixture()
            fixture.api.onRegister = {
                fixture.locale = "uk-UA"
                fixture.collector.capture()
            }
            val sync = fixture.coordinator()
            sync.synchronize(0)
            assertTrue(fixture.repository.read().revision > fixture.repository.read().lastSyncedRevision)
            assertEquals(1, fixture.scheduler.enqueued)
            fixture.api.onRegister = {}
            sync.synchronize(0)
            assertEquals(listOf("en-US", "uk-UA"), fixture.api.registrations.map { it.locale })
            assertEquals(fixture.repository.read().revision, fixture.repository.read().lastSyncedRevision)
        }

    @Test fun `only acknowledged open events are removed after a partial network failure`() =
        runBlocking {
            val fixture = CoreFixture()
            fixture.repository.update { it.copy(pendingOpenedMessages = listOf("first", "second")) }
            fixture.api.onOpened = { if (it == "second") throw HttpFailure(503) }
            val sync = fixture.coordinator()
            assertEquals(SyncOutcome.RETRY, sync.synchronize(0))
            assertEquals(listOf("second"), fixture.repository.read().pendingOpenedMessages)
            fixture.api.onOpened = {}
            assertEquals(SyncOutcome.SUCCESS, sync.synchronize(1))
            assertEquals(listOf("first", "second"), fixture.api.openedMessages)
            assertTrue(
                fixture.repository
                    .read()
                    .pendingOpenedMessages
                    .isEmpty(),
            )
            assertEquals(1, fixture.api.registrations.size)
        }

    @Test fun `retry policy distinguishes temporary errors permanent errors and the retry limit`() =
        runBlocking {
            for (error in listOf(IOException("offline"), HttpFailure(503), HttpFailure(429))) {
                val fixture = CoreFixture()
                fixture.api.failure = error
                val sync = fixture.coordinator()
                assertEquals(SyncOutcome.RETRY, sync.synchronize(0))
                assertEquals(SyncOutcome.FAILURE, sync.synchronize(8))
                assertNull(fixture.repository.read().lastSyncedAt)
            }
            val fixture = CoreFixture()
            fixture.api.failure = HttpFailure(403)
            assertEquals(SyncOutcome.FAILURE, fixture.coordinator().synchronize(0))
            assertEquals("HTTP 403", fixture.repository.read().syncError)
        }

    @Test fun `cancellation is propagated and does not acknowledge work`() {
        val fixture = CoreFixture()
        fixture.api.config = TEST_FIREBASE
        fixture.provider.fetch = { throw CancellationException("work cancelled") }
        assertThrows(CancellationException::class.java) { runBlocking { fixture.coordinator().synchronize(0) } }
        assertTrue(fixture.api.registrations.isEmpty())
        assertNull(fixture.repository.read().syncError)
    }

    @Test fun `token timeout allows metadata registration but parent timeout still cancels the sync`() {
        val fixture = CoreFixture()
        fixture.api.config = TEST_FIREBASE
        fixture.provider.fetch = {
            delay(1_000)
            "late-token"
        }
        assertEquals(SyncOutcome.RETRY, runBlocking { fixture.coordinator(1).synchronize(0) })
        assertNotNull(fixture.repository.read().pushError)
        assertFalse(fixture.api.registrations.isEmpty())
        assertThrows(TimeoutCancellationException::class.java) {
            runBlocking { withTimeout(10) { fixture.coordinator(10_000).synchronize(0) } }
        }
    }

    @Test fun `removed Firebase configuration clears cached options and the old token`() =
        runBlocking {
            val fixture = CoreFixture()
            fixture.api.config = TEST_FIREBASE
            val sync = fixture.coordinator()
            sync.synchronize(0)
            fixture.api.config = null
            sync.synchronize(0)
            assertNull(fixture.repository.read().firebase)
            assertNull(
                fixture.api.registrations
                    .last()
                    .fcmToken,
            )
        }
}
