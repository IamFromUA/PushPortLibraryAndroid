// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

import android.app.Application
import dev.pushport.sdk.internal.core.SessionTracker
import dev.pushport.sdk.internal.core.SyncCoordinator
import dev.pushport.sdk.internal.core.SyncOutcome
import dev.pushport.sdk.internal.core.TelemetryTracker
import dev.pushport.sdk.internal.core.TokenRefresher
import dev.pushport.sdk.internal.model.InstallationIdentity
import dev.pushport.sdk.internal.model.RemoteConfiguration
import dev.pushport.sdk.internal.model.TelemetryEvent
import dev.pushport.sdk.internal.network.NotificationImageDownloader
import dev.pushport.sdk.internal.ports.Clock
import dev.pushport.sdk.internal.ports.HttpFailure
import dev.pushport.sdk.internal.ports.InstallationApi
import dev.pushport.sdk.internal.ports.InstallationApiFactory
import dev.pushport.sdk.internal.storage.InstallationStateJson
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Random
import java.util.UUID
import javax.imageio.ImageIO

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class NotificationTelemetryTest {
    @Test fun `outbox survives storage keeps original timestamps coalesces scheduling and respects consent`() {
        val fixture = CoreFixture()
        val tracker = TelemetryTracker(fixture.repository, fixture.scheduler) { 1000L }
        val message = UUID.randomUUID().toString()
        tracker.record("received", message)
        tracker.record("posted", message)
        tracker.record("received", message)
        assertEquals(
            2,
            fixture.repository
                .read()
                .pendingTelemetry.size,
        )
        val codec = InstallationStateJson()
        assertEquals(fixture.repository.read(), codec.decode(codec.encode(fixture.repository.read())))
        assertEquals(
            1000L,
            fixture.repository
                .read()
                .pendingTelemetry
                .first()
                .occurredAt,
        )
        fixture.repository.update { it.copy(consentRequired = true, consentGiven = false) }
        tracker.record("clicked", message)
        assertEquals(
            2,
            fixture.repository
                .read()
                .pendingTelemetry.size,
        )
        fixture.repository.update { it.copy(consentGiven = true) }
        repeat(1002) { tracker.record("session_started", message, "influenced") }
        assertEquals(
            1000,
            fixture.repository
                .read()
                .pendingTelemetry.size,
        )
        assertEquals(4L, fixture.repository.read().telemetryDropped)
    }

    @Test fun `telemetry retries preserve IDs acknowledge only received IDs and flush more than one batch`() =
        runBlocking {
            val fixture = CoreFixture()
            var fail = true
            val seen = mutableListOf<List<String>>()
            val api =
                object : InstallationApi by fixture.api {
                    override fun settings(packageName: String) =
                        RemoteConfiguration(
                            null,
                            telemetryVersion = 1,
                            imageLimitBytes =
                                8 * 1024 * 1024,
                        )

                    override fun telemetry(
                        identity: InstallationIdentity,
                        events: List<TelemetryEvent>,
                        dropped: Long,
                    ): List<String> {
                        seen += events.map { it.id }
                        if (fail) throw IOException("offline")
                        return events.map { it.id }
                    }
                }
            val tracker = TelemetryTracker(fixture.repository, fixture.scheduler) { fixture.now }
            repeat(205) { tracker.record("session_started", reason = "organic") }
            val coordinator =
                SyncCoordinator(
                    fixture.repository,
                    fixture.collector,
                    TokenRefresher(
                        fixture.repository,
                        fixture.provider,
                    ),
                    InstallationApiFactory {
                        api
                    },
                    fixture.scheduler,
                    Clock { fixture.now },
                    true,
                )
            assertEquals(SyncOutcome.RETRY, coordinator.synchronize(0))
            assertEquals(
                205,
                fixture.repository
                    .read()
                    .pendingTelemetry.size,
            )
            fail = false
            assertEquals(SyncOutcome.SUCCESS, coordinator.synchronize(1))
            assertEquals(seen[0], seen[1])
            assertTrue(
                fixture.repository
                    .read()
                    .pendingTelemetry
                    .isEmpty(),
            )
            assertEquals(8 * 1024 * 1024, fixture.repository.read().imageLimitBytes)
        }

    @Test fun `old server disables telemetry without blocking registration or legacy clicks`() =
        runBlocking {
            val fixture = CoreFixture()
            TelemetryTracker(fixture.repository, fixture.scheduler).record("session_started", reason = "organic")
            val api =
                object : InstallationApi by fixture.api {
                    override fun settings(packageName: String) = RemoteConfiguration(null, telemetryVersion = 1)

                    override fun telemetry(
                        identity: InstallationIdentity,
                        events: List<TelemetryEvent>,
                        dropped: Long,
                    ): List<String> = throw HttpFailure(404)
                }
            val coordinator =
                SyncCoordinator(
                    fixture.repository,
                    fixture.collector,
                    TokenRefresher(
                        fixture.repository,
                        fixture.provider,
                    ),
                    InstallationApiFactory {
                        api
                    },
                    fixture.scheduler,
                    Clock { fixture.now },
                    true,
                )
            assertEquals(SyncOutcome.SUCCESS, coordinator.synchronize(0))
            assertEquals(1, fixture.api.registrations.size)
            assertTrue(!fixture.repository.read().telemetryEnabled)
        }

    @Test fun `return attribution does not count short activity transitions or label a clicked push as influenced`() {
        val fixture = CoreFixture()
        var wall = 1_000_000L
        var elapsed = 0L
        val tracker = TelemetryTracker(fixture.repository, fixture.scheduler) { wall }
        val sessions = SessionTracker(fixture.repository, { wall }, { elapsed }, tracker)
        val message = UUID.randomUUID().toString()
        tracker.record("received", message)
        sessions.foreground()
        elapsed += 5000
        sessions.background()
        wall += 5000
        sessions.foreground()
        sessions.background()
        assertEquals(
            1,
            fixture.repository
                .read()
                .pendingTelemetry
                .count { it.type == "session_started" },
        )
        wall += 35000
        sessions.foreground()
        sessions.background()
        tracker.record("clicked", message)
        wall += 35000
        sessions.foreground()
        sessions.background()
        assertEquals(
            listOf("influenced", "influenced", "direct"),
            fixture.repository
                .read()
                .pendingTelemetry
                .filter {
                    it.type ==
                        "session_started"
                }.map { it.reason },
        )
        wall += 120000
        sessions.foreground()
        assertEquals(
            "organic",
            fixture.repository
                .read()
                .pendingTelemetry
                .last {
                    it.type ==
                        "session_started"
                }.reason,
        )
    }

    @Test fun `default image limit accepts two MiB and reports configured size failures`() {
        val image = BufferedImage(800, 800, BufferedImage.TYPE_INT_RGB)
        val random = Random(123)
        for (y in 0 until 800)for (x in 0 until 800)image.setRGB(x, y, random.nextInt())
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        assertTrue(bytes.size > 1_048_576)
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    Response
                        .Builder()
                        .request(
                            chain.request(),
                        ).protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(bytes.toResponseBody("image/png".toMediaType()))
                        .build()
                }.build()
        val downloader = NotificationImageDownloader(client)
        assertNotNull(downloader.downloadResult("https://images.example.org/image.png").bitmap)
        val rejected = downloader.downloadResult("https://images.example.org/image.png", 1_048_576)
        assertNull(rejected.bitmap)
        assertEquals("size_limit", rejected.reason)
        assertEquals(bytes.size.toLong(), rejected.sizeBytes)
        assertEquals(200, rejected.httpStatus)
        assertEquals("invalid_url", downloader.downloadResult("http://localhost/image").reason)
        val html =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    Response
                        .Builder()
                        .request(
                            chain.request(),
                        ).protocol(
                            Protocol.HTTP_1_1,
                        ).code(200)
                        .message("OK")
                        .body("Missing parameters".toResponseBody("text/plain".toMediaType()))
                        .build()
                }.build()
        assertEquals("not_image", NotificationImageDownloader(html).downloadResult("https://images.example.org/image").reason)
    }
}
