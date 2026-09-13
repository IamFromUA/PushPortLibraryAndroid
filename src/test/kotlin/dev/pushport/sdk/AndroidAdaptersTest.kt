// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

import android.app.Application
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dev.pushport.sdk.internal.firebase.FirebasePushTokenProvider
import dev.pushport.sdk.internal.model.PushProtocol
import dev.pushport.sdk.internal.network.HttpInstallationApi
import dev.pushport.sdk.internal.network.UrlConnectionTransport
import dev.pushport.sdk.internal.platform.AndroidDeviceInfoProvider
import dev.pushport.sdk.internal.ports.HttpFailure
import dev.pushport.sdk.internal.ports.NotificationStateProvider
import dev.pushport.sdk.internal.storage.AtomicInstallationRepository
import dev.pushport.sdk.internal.storage.InstallationStateJson
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
// API 31+ AtomicFile relies on POSIX rename-overwrite, unavailable in Windows Robolectric.
// The current Android implementation is also exercised by the live emulator upgrade test.
@Config(sdk = [28], application = Application::class)
class AndroidAdaptersTest {
    private lateinit var context: Context
    private lateinit var file: File

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        file = File(context.noBackupFilesDir, "pushport-installation.json")
        file.delete()
        File(file.path + ".bak").delete()
    }

    @After fun tearDown() {
        FirebaseApp.getApps(context).toList().forEach { it.delete() }
        context.getSystemService(NotificationManager::class.java).cancelAll()
        file.delete()
        File(file.path + ".bak").delete()
    }

    @Test fun `legacy data upgrades in place while keeping identity token revisions and queued events`() {
        file.writeText(legacyState())
        val repository = AtomicInstallationRepository(context)
        val before = repository.read()
        assertEquals(null, before.snapshot?.androidId)
        repository.update { it.copy(subscribed = false) }
        assertEquals(before.copy(subscribed = false), AtomicInstallationRepository(context).read())
        assertEquals(TEST_IDENTITY, repository.read().identity)
        assertEquals("legacy-fcm-token", repository.read().fcmToken)
        assertEquals(12, repository.read().revision)
        assertEquals(11, repository.read().lastSyncedRevision)
        assertEquals(1, repository.read().pendingOpenedMessages.size)
        assertEquals(1, JSONObject(file.readText()).getInt("schemaVersion"))
    }

    @Test fun `an interrupted legacy AtomicFile write restores its backup rather than creating a new identity`() {
        File(file.path + ".bak").writeText(legacyState())
        val state = AtomicInstallationRepository(context).read()
        assertEquals(TEST_IDENTITY, state.identity)
        assertEquals("legacy-fcm-token", state.fcmToken)
    }

    @Test fun `unknown future schema fails without overwriting the stored installation`() {
        val original = JSONObject(legacyState()).put("schemaVersion", 99).toString()
        file.writeText(original)
        assertThrows(IllegalArgumentException::class.java) {
            AtomicInstallationRepository(context).update { it.copy(subscribed = false) }
        }
        assertEquals(original, file.readText())
    }

    @Test fun `HTTP adapter uses existing wire fields and never follows redirects with the installation secret`() {
        MockWebServer().use { source ->
            MockWebServer().use { destination ->
                source.enqueue(MockResponse().setResponseCode(302).setHeader("Location", destination.url("/stolen")))
                val api =
                    HttpInstallationApi(UrlConnectionTransport(PushPortConfig(TEST_APP_ID, source.url("/").toString().trimEnd('/'), true)))
                val error = assertThrows(HttpFailure::class.java) { api.register(TEST_IDENTITY, testSnapshot()) }
                assertEquals(302, error.status)
                assertFalse(error.retryable)
                assertEquals(0, destination.requestCount)
                val sent = source.takeRequest()
                assertEquals("Bearer ${TEST_IDENTITY.secret}", sent.getHeader("Authorization"))
                assertEquals("PushPort-Android", sent.getHeader("User-Agent"))
                val body = JSONObject(sent.body.readUtf8())
                assertTrue(body.isNull("fcmToken"))
                assertTrue(body.isNull("androidId"))
                assertEquals("en-US", body.getString("locale"))
                assertFalse(body.has("secret"))
            }
        }
    }

    @Test fun `oversized network responses are bounded`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(65_537)))
            val transport = UrlConnectionTransport(PushPortConfig(TEST_APP_ID, server.url("/").toString().trimEnd('/'), true))
            assertThrows(IllegalArgumentException::class.java) { transport.request("GET", "/config") }
        }
    }

    @Test fun `malformed configuration is not mistaken for an explicit Firebase disconnect`() {
        MockWebServer().use { server ->
            val api = HttpInstallationApi(UrlConnectionTransport(PushPortConfig(TEST_APP_ID, server.url("/").toString(), true)))
            for (body in listOf("{}", "{\"firebase\":[]}", "{\"firebase\":\"invalid\"}")) {
                server.enqueue(MockResponse().setBody(body))
                assertThrows(Exception::class.java) { api.configuration("dev.pushport.sample") }
            }
            server.enqueue(MockResponse().setBody("{\"firebase\":null}"))
            assertEquals(null, api.configuration("dev.pushport.sample"))
        }
    }

    @Test fun `WorkManager adapter registers without Firebase and maps transient errors to retry`() =
        runBlocking {
            MockWebServer().use { server ->
                val androidId = "0123456789abcdef"
                Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, androidId)
                context.applicationInfo.flags = context.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
                val repository = AtomicInstallationRepository(context)
                repository.update {
                    it.copy(
                        config = PushPortConfig(TEST_APP_ID, "http://127.0.0.1:${server.port}", true),
                        identity = TEST_IDENTITY,
                    )
                }
                server.enqueue(MockResponse().setBody("{\"firebase\":null}"))
                server.enqueue(MockResponse().setResponseCode(204))
                assertEquals(ListenableWorker.Result.success(), TestListenableWorkerBuilder<SyncWorker>(context).build().doWork())
                assertEquals("GET", server.takeRequest().method)
                val registration = server.takeRequest()
                assertEquals("PUT", registration.method)
                assertEquals(androidId, JSONObject(registration.body.readUtf8()).getString("androidId"))
                assertEquals(androidId, AtomicInstallationRepository(context).read().snapshot?.androidId)
                assertEquals(TEST_IDENTITY, repository.read().identity)
                assertTrue(repository.read().lastSyncedAt!! > 0)
                server.enqueue(MockResponse().setResponseCode(503))
                assertEquals(ListenableWorker.Result.retry(), TestListenableWorkerBuilder<SyncWorker>(context).build().doWork())
                assertEquals("HTTP 503", repository.read().syncError)
            }
        }

    @Test fun `device snapshot reads Android ID as lowercase hex without fixed width padding`() {
        val provider = AndroidDeviceInfoProvider(context, NotificationStateProvider { true })
        for (value in listOf("0123456789ABCDEF", "1a")) {
            Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, value)
            val snapshot = provider.snapshot(null, null)
            assertEquals(value.lowercase(), snapshot.androidId)
            assertFalse(snapshot.toString().contains(snapshot.androidId!!))
        }
    }

    @Test fun `missing or malformed Android ID does not prevent metadata collection`() {
        val provider = AndroidDeviceInfoProvider(context, NotificationStateProvider { true })
        for (value in listOf(null, "", "unknown", "0", "0000000000000000", "0123456789abcdef0")) {
            Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, value)
            val snapshot = provider.snapshot(null, null)
            assertEquals(null, snapshot.androidId)
            assertEquals(context.packageName, snapshot.packageName)
        }
    }

    @Test fun `denied access to Android ID does not prevent metadata collection`() {
        val restricted =
            object : ContextWrapper(context) {
                override fun getContentResolver(): ContentResolver = throw SecurityException("Unavailable")
            }
        val snapshot = AndroidDeviceInfoProvider(restricted, NotificationStateProvider { true }).snapshot(null, null)
        assertEquals(null, snapshot.androidId)
        assertEquals(context.packageName, snapshot.packageName)
    }

    @Test fun `Firebase adapter keeps the host Firebase app separate and reuses its own app`() {
        val host =
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions
                    .Builder()
                    .setApplicationId("1:999:android:abcdef")
                    .setProjectId("host-project")
                    .setApiKey("AIza" + "0".repeat(35)) // Synthetic key for the host-app fixture.
                    .setGcmSenderId("999")
                    .build(),
            )
        val provider = FirebasePushTokenProvider(context)
        val sdk = provider.application(TEST_FIREBASE.copy(senderId = "456"))
        assertNotSame(host, sdk)
        assertSame(host, FirebaseApp.getInstance())
        assertEquals("host-project", host.options.projectId)
        assertEquals("test-project", sdk.options.projectId)
        assertEquals("456", sdk.options.gcmSenderId)
        assertSame(sdk, provider.application(TEST_FIREBASE.copy(senderId = "456")))
    }

    @Test fun `Android receiver filters messages and opens through the retained internal Activity`() {
        val repository = AtomicInstallationRepository(context)
        repository.update { it.copy(config = PushPortConfig(TEST_APP_ID, "https://example.test"), identity = TEST_IDENTITY) }
        val receiver = PushPortMessageReceiver()
        val id = UUID.randomUUID().toString()
        val push =
            Intent(PushProtocol.RECEIVE_ACTION)
                .putExtra(PushProtocol.MESSAGE_ID, id)
                .putExtra(PushProtocol.APP_ID, TEST_APP_ID)
                .putExtra("title", "Title")
                .putExtra("body", "Body")
        assertFalse(receiver.accept(context, Intent(push).putExtra("from", "google.com/iid")))
        assertFalse(receiver.accept(context, Intent(push).putExtra(PushProtocol.APP_ID, "other")))
        assertTrue(receiver.accept(context, push))
        assertTrue(receiver.accept(context, push))
        val shown = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
        assertEquals(1, shown.size)
        val click = shadowOf(shown.single().contentIntent).savedIntent
        assertEquals(NotificationOpenActivity::class.java.name, click.component!!.className)
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val activity = Robolectric.buildActivity(NotificationOpenActivity::class.java, click).create()
        assertTrue(activity.get().isFinishing)
        assertEquals(listOf(id), repository.read().pendingOpenedMessages)
        activity.destroy()
    }

    private fun legacyState(): String = requireNotNull(javaClass.getResource("/installation-v0.json")).readText()
}
