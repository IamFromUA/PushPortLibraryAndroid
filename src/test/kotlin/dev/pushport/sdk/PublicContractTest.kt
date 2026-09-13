// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicContractTest {
    @Test fun `status string never reveals the registration token`() {
        val fixture = CoreFixture()
        fixture.repository.update { it.copy(fcmToken = "private-fcm-token") }
        fixture.collector.capture()
        val status = fixture.controller.status()
        assertEquals("private-fcm-token", status.fcmToken)
        assertFalse(status.toString().contains("private-fcm-token"))
        assertTrue(status.toString().contains("hasFcmToken=true"))
    }

    @Test fun `invalid endpoint input consistently raises an argument error`() {
        for (url in listOf("not a URI", "https://example.test:0", "https://example.test:65536", "//example.test")) {
            assertThrows(IllegalArgumentException::class.java) { PushPortConfig(TEST_APP_ID, url).validate(true) }
        }
        assertEquals("https://example.test:443/api", PushPortConfig(TEST_APP_ID, "https://example.test:443/api/").validate(false).serverUrl)
    }

    @Test fun `locale validation is atomic and automatic detection can be restored`() {
        val fixture = CoreFixture()
        fixture.controller.setLocale("de-de")
        assertEquals("de-DE", fixture.controller.status().locale)
        val before = fixture.repository.read()
        for (tag in listOf("de_DE", "", "und", "de-@")) {
            assertThrows(IllegalArgumentException::class.java) { fixture.controller.setLocale(tag) }
            assertEquals(before, fixture.repository.read())
        }
        fixture.controller.setLocale(null)
        assertEquals("en-US", fixture.controller.status().locale)
    }

    @Test fun `Java callers can construct default connection settings`() {
        val constructor = PushPortConfig::class.java.getConstructor(String::class.java, String::class.java)
        val config = constructor.newInstance(TEST_APP_ID, "https://example.test")
        assertFalse(config.allowInsecureLocalhost)
    }

    @Test fun `status does not expose the repository's mutable locale list`() {
        val fixture = CoreFixture()
        val locales = mutableListOf("en-US", "de-DE")
        fixture.repository.update { it.copy(snapshot = testSnapshot().copy(systemLocales = locales)) }
        val status = fixture.controller.status()
        locales.clear()
        assertEquals(listOf("en-US", "de-DE"), status.systemLocales)
    }
}
