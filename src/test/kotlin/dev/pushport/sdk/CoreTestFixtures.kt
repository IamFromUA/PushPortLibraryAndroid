// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

import dev.pushport.sdk.internal.core.InstallationController
import dev.pushport.sdk.internal.core.SnapshotCollector
import dev.pushport.sdk.internal.core.SyncCoordinator
import dev.pushport.sdk.internal.core.TokenRefresher
import dev.pushport.sdk.internal.model.DeviceSnapshot
import dev.pushport.sdk.internal.model.FirebaseConfiguration
import dev.pushport.sdk.internal.model.InstallationIdentity
import dev.pushport.sdk.internal.model.InstallationState
import dev.pushport.sdk.internal.ports.Clock
import dev.pushport.sdk.internal.ports.DeviceInfoProvider
import dev.pushport.sdk.internal.ports.InstallationApi
import dev.pushport.sdk.internal.ports.InstallationApiFactory
import dev.pushport.sdk.internal.ports.InstallationRepository
import dev.pushport.sdk.internal.ports.NotificationStateProvider
import dev.pushport.sdk.internal.ports.PushTokenProvider
import dev.pushport.sdk.internal.ports.SyncScheduler
import java.util.Locale

internal const val TEST_APP_ID = "11111111-1111-4111-8111-111111111111"
internal val TEST_IDENTITY = InstallationIdentity("22222222-2222-4222-8222-222222222222", "synthetic-installation-secret")
internal val TEST_FIREBASE = FirebaseConfiguration("1:123:android:abcdef", "synthetic-api-key", "123", "test-project")

internal class MemoryRepository(
    initial: InstallationState = InstallationState(),
) : InstallationRepository {
    private var state = initial

    @Synchronized
    override fun read(): InstallationState = state

    @Synchronized
    override fun update(transform: (InstallationState) -> InstallationState): InstallationState = transform(state).also { state = it }
}

internal class RecordingScheduler : SyncScheduler {
    var enqueued = 0
    var periodic = 0

    override fun enqueue() {
        enqueued++
    }

    override fun schedulePeriodic() {
        periodic++
    }
}

internal class StubTokenProvider : PushTokenProvider {
    var fetch: suspend (FirebaseConfiguration) -> String = { "test-fcm-token" }
    var restored: FirebaseConfiguration? = null

    override fun restore(configuration: FirebaseConfiguration) {
        restored = configuration
    }

    override suspend fun token(configuration: FirebaseConfiguration): String = fetch(configuration)
}

internal class RecordingApi : InstallationApi {
    var config: FirebaseConfiguration? = null
    var failure: Exception? = null
    val registrations = mutableListOf<DeviceSnapshot>()
    val openedMessages = mutableListOf<String>()
    var onRegister: (DeviceSnapshot) -> Unit = {}
    var onOpened: (String) -> Unit = {}

    override fun configuration(packageName: String): FirebaseConfiguration? {
        failure?.let { throw it }
        return config
    }

    override fun register(
        identity: InstallationIdentity,
        snapshot: DeviceSnapshot,
    ) {
        onRegister(snapshot)
        registrations += snapshot
    }

    override fun opened(
        identity: InstallationIdentity,
        messageId: String,
    ) {
        onOpened(messageId)
        openedMessages += messageId
    }
}

internal class CoreFixture {
    val repository = MemoryRepository()
    val scheduler = RecordingScheduler()
    val provider = StubTokenProvider()
    val api = RecordingApi()
    var now = 1_000_000L
    var locale = "en-US"
    val collector =
        SnapshotCollector(
            repository,
            DeviceInfoProvider { override, observed ->
                testSnapshot(override ?: observed?.firstOrNull() ?: locale)
            },
        )
    val controller =
        InstallationController(
            repository,
            collector,
            scheduler,
            NotificationStateProvider { true },
            { TEST_IDENTITY },
            true,
        )

    init {
        controller.initialize(PushPortConfig(TEST_APP_ID, "https://example.test"))
    }

    fun coordinator(tokenTimeoutMillis: Long = 20_000): SyncCoordinator =
        SyncCoordinator(
            repository,
            collector,
            TokenRefresher(repository, provider, tokenTimeoutMillis),
            InstallationApiFactory { api },
            scheduler,
            Clock { now },
            true,
        )
}

internal fun testSnapshot(locale: String = "en-US"): DeviceSnapshot =
    DeviceSnapshot(
        packageName = "dev.pushport.sample",
        language = Locale.forLanguageTag(locale).language,
        locale = locale,
        appLocales = listOf(locale),
        systemLocales = listOf("en-US"),
        timezone = "Europe/Berlin",
        notificationsEnabled = true,
        sdkVersion = "0.1.0",
        appVersion = "1.0",
        appVersionCode = 1,
        osVersion = "9",
        androidApi = 28,
        manufacturer = "test",
        model = "test-device",
    )
