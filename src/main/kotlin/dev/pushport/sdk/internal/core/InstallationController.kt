// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.core

import dev.pushport.sdk.PushPortConfig
import dev.pushport.sdk.PushPortStatus
import dev.pushport.sdk.internal.model.InstallationIdentity
import dev.pushport.sdk.internal.ports.InstallationRepository
import dev.pushport.sdk.internal.ports.NotificationStateProvider
import dev.pushport.sdk.internal.ports.SyncScheduler
import java.util.IllformedLocaleException
import java.util.Locale

internal class InstallationController(
    private val repository: InstallationRepository,
    private val collector: SnapshotCollector,
    private val scheduler: SyncScheduler,
    private val permissions: NotificationStateProvider,
    private val newIdentity: () -> InstallationIdentity,
    private val debuggable: Boolean,
) {
    fun initialize(config: PushPortConfig) {
        val validated = config.validate(debuggable)
        repository.update { state ->
            val previous = state.config
            require(previous == null || (previous.appId == validated.appId && previous.serverUrl == validated.serverUrl)) {
                "An installation belongs to one PushPort app. Clear test application data before changing appId or serverUrl."
            }
            state.copy(config = validated, identity = state.identity ?: if (state.collectionAllowed) newIdentity() else null)
        }
    }

    fun sync(observedLocales: List<String>? = null) {
        if (!repository.read().collectionAllowed) return
        if (collector.capture(observedLocales) != null) scheduler.enqueue()
    }

    fun environmentChanged(clearObservedLocales: Boolean) {
        if (clearObservedLocales) repository.update { it.copy(observedLocales = null) }
        sync()
    }

    fun setSubscribed(subscribed: Boolean) {
        requireConfigured()
        repository.update { it.copy(subscribed = subscribed) }
        sync()
    }

    fun setLocale(languageTag: String?) {
        requireConfigured()
        val normalized =
            languageTag?.let {
                try {
                    Locale
                        .Builder()
                        .setLanguageTag(it)
                        .build()
                        .toLanguageTag()
                } catch (_: IllformedLocaleException) {
                    throw IllegalArgumentException("Use a BCP-47 locale such as ru-RU or de-DE")
                }
            }
        require(normalized == null || (normalized.length <= 100 && normalized != "und")) {
            "Use a BCP-47 locale such as ru-RU or de-DE"
        }
        repository.update { it.copy(localeOverride = normalized) }
        sync()
    }

    fun status(): PushPortStatus {
        val state = repository.read()
        return PushPortStatus(
            configured = state.config != null,
            installationId = state.identity?.id,
            fcmToken = state.fcmToken,
            locale = state.snapshot?.locale,
            systemLocales =
                state.snapshot
                    ?.systemLocales
                    .orEmpty()
                    .toList(),
            notificationsEnabled = permissions.isEnabled(),
            subscribed = state.subscribed,
            lastSyncedAt = state.lastSyncedAt,
            syncError = state.syncError,
            pushError = state.pushError,
        )
    }

    private fun requireConfigured() {
        require(repository.read().config != null) { "Initialize PushPort first" }
    }
}
