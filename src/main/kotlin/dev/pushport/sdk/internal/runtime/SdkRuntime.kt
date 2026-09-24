// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.runtime

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Base64
import dev.pushport.sdk.PushPortConfig
import dev.pushport.sdk.internal.core.InstallationController
import dev.pushport.sdk.internal.core.NotificationOpenTracker
import dev.pushport.sdk.internal.core.PushHandler
import dev.pushport.sdk.internal.core.SessionTracker
import dev.pushport.sdk.internal.core.SnapshotCollector
import dev.pushport.sdk.internal.core.SyncCoordinator
import dev.pushport.sdk.internal.core.TokenRefresher
import dev.pushport.sdk.internal.firebase.FirebasePushTokenProvider
import dev.pushport.sdk.internal.model.InstallationIdentity
import dev.pushport.sdk.internal.model.PushProtocol
import dev.pushport.sdk.internal.model.isCanonicalUuid
import dev.pushport.sdk.internal.network.HttpInstallationApi
import dev.pushport.sdk.internal.network.UrlConnectionTransport
import dev.pushport.sdk.internal.platform.AndroidDeviceInfoProvider
import dev.pushport.sdk.internal.platform.AndroidNotificationPresenter
import dev.pushport.sdk.internal.platform.AndroidNotificationState
import dev.pushport.sdk.internal.platform.ApplicationObserver
import dev.pushport.sdk.internal.platform.WorkManagerSyncScheduler
import dev.pushport.sdk.internal.ports.Clock
import dev.pushport.sdk.internal.ports.InstallationApiFactory
import dev.pushport.sdk.internal.storage.AtomicInstallationRepository
import java.security.SecureRandom
import java.util.UUID

/** The only composition root. Core services receive dependencies through constructors. */
internal class SdkRuntime private constructor(
    private val application: Application,
) {
    private val repository = AtomicInstallationRepository(application)
    private val notifications = AndroidNotificationState(application)
    private val presenter = AndroidNotificationPresenter(application, notifications)
    private val scheduler = WorkManagerSyncScheduler(application)
    private val collector = SnapshotCollector(repository, AndroidDeviceInfoProvider(application, notifications))
    private val tokens = TokenRefresher(repository, FirebasePushTokenProvider(application))
    private val debuggable = application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val sessions = SessionTracker(repository, System::currentTimeMillis, android.os.SystemClock::elapsedRealtime)

    val users =
        dev.pushport.sdk.internal.core
            .UserController(repository, scheduler)

    val controller = InstallationController(repository, collector, scheduler, notifications, ::newIdentity, debuggable)
    val synchronizer =
        SyncCoordinator(
            repository,
            collector,
            tokens,
            InstallationApiFactory { HttpInstallationApi(UrlConnectionTransport(it)) },
            scheduler,
            Clock(System::currentTimeMillis),
            debuggable,
        )
    val pushHandler = PushHandler(repository, presenter)
    private val openTracker = NotificationOpenTracker(repository, scheduler)
    private val observer =
        ApplicationObserver(
            application,
            onResume = { locales, intent ->
                handleNotificationIntent(intent)
                controller.sync(locales)
            },
            onConfigurationChange = { controller.sync() },
            onForeground = {
                sessions.foreground()
                controller.sync()
            },
            onBackground = {
                sessions.background()
                controller.sync()
            },
            onHeartbeat = {
                sessions.checkpoint()
                controller.sync()
            },
        )

    @Synchronized
    fun initialize(config: PushPortConfig) {
        controller.initialize(config)
        presenter.createChannel()
        observer.register()
        if (!repository.read().collectionAllowed) return
        tokens.restore()
        if (observer.isForeground) sessions.foreground()
        controller.sync()
        scheduler.schedulePeriodic()
    }

    @Synchronized
    fun consent(
        required: Boolean? = null,
        given: Boolean? = null,
    ) {
        sessions.background()
        repository.update { it.copy(consentRequired = required ?: it.consentRequired, consentGiven = given ?: it.consentGiven) }
        repository.read().config?.let(::initialize)
    }

    fun restore(): Boolean {
        val config = repository.read().config ?: return false
        initialize(config)
        return true
    }

    fun handleNotificationIntent(intent: Intent?) {
        if (!repository.read().collectionAllowed) return
        val messageId = intent?.getStringExtra(PushProtocol.MESSAGE_ID) ?: return
        if (!isCanonicalUuid(messageId)) return
        intent.removeExtra(PushProtocol.MESSAGE_ID)
        openTracker.opened(messageId)
        androidx.work.WorkManager
            .getInstance(application)
            .cancelUniqueWork("pushport-image-$messageId")
    }

    fun canEnrichNotification(
        appId: String,
        messageId: String,
    ): Boolean {
        val state = repository.read()
        return state.collectionAllowed && state.config?.appId == appId && state.subscribed && messageId in state.receivedMessages &&
            messageId !in state.pendingOpenedMessages && notifications.isEnabled() && presenter.isVisible(messageId)
    }

    fun enrichNotification(
        appId: String,
        messageId: String,
        image: android.graphics.Bitmap,
    ) {
        if (canEnrichNotification(appId, messageId)) presenter.enrich(messageId, image)
    }

    private fun newIdentity(): InstallationIdentity {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val secret = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return InstallationIdentity(UUID.randomUUID().toString(), secret)
    }

    companion object {
        private var current: SdkRuntime? = null

        @Synchronized
        fun from(context: Context): SdkRuntime {
            val application = context.applicationContext as Application
            return current?.takeIf { it.application === application }
                ?: SdkRuntime(application).also { current = it }
        }
    }
}
