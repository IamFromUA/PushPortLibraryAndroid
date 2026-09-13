// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

import dev.pushport.sdk.internal.core.NotificationOpenTracker
import dev.pushport.sdk.internal.core.PushHandler
import dev.pushport.sdk.internal.model.PushMessage
import dev.pushport.sdk.internal.ports.NotificationPresenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PushHandlerTest {
    @Test fun `unrelated invalid and repeated messages cannot produce extra notifications`() {
        val fixture = CoreFixture()
        val shown = mutableListOf<PushMessage>()
        val handler = PushHandler(fixture.repository, presenter(shown))
        val message = PushMessage(TEST_APP_ID, UUID.randomUUID().toString(), "Title", "Body")
        assertFalse(handler.accept(message.copy(appId = "another-app")))
        assertFalse(handler.accept(message.copy(id = "malformed")))
        assertTrue(handler.accept(message))
        assertTrue(handler.accept(message))
        assertEquals(listOf(message), shown)
        assertEquals(listOf(message.id), fixture.repository.read().receivedMessages)
    }

    @Test fun `unsubscribed installations consume their own messages without displaying them`() {
        val fixture = CoreFixture()
        fixture.repository.update { it.copy(subscribed = false) }
        val shown = mutableListOf<PushMessage>()
        val handler = PushHandler(fixture.repository, presenter(shown))
        assertTrue(handler.accept(PushMessage(TEST_APP_ID, UUID.randomUUID().toString(), "Title", "Body")))
        assertTrue(shown.isEmpty())
        assertEquals(
            1,
            fixture.repository
                .read()
                .receivedMessages.size,
        )
    }

    @Test fun `message deduplication and pending opens are bounded and preserve newest entries`() {
        val fixture = CoreFixture()
        val handler = PushHandler(fixture.repository, presenter(mutableListOf()))
        val tracker = NotificationOpenTracker(fixture.repository, fixture.scheduler)
        val messages = List(105) { UUID.randomUUID().toString() }
        for (id in messages) {
            handler.accept(PushMessage(TEST_APP_ID, id, "Title", "Body"))
            tracker.opened(id)
            tracker.opened(id)
        }
        tracker.opened("invalid")
        assertEquals(messages.takeLast(100), fixture.repository.read().pendingOpenedMessages)
        assertEquals(messages.takeLast(100), fixture.repository.read().receivedMessages)
    }

    private fun presenter(shown: MutableList<PushMessage>): NotificationPresenter =
        object : NotificationPresenter {
            override fun createChannel() = Unit

            override fun show(message: PushMessage) {
                shown += message
            }
        }
}
