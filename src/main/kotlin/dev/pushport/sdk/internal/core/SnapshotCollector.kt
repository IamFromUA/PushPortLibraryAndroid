// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.core

import dev.pushport.sdk.internal.model.DeviceSnapshot
import dev.pushport.sdk.internal.ports.DeviceInfoProvider
import dev.pushport.sdk.internal.ports.InstallationRepository

internal class SnapshotCollector(
    private val repository: InstallationRepository,
    private val deviceInfo: DeviceInfoProvider,
) {
    fun capture(observedLocales: List<String>? = null): DeviceSnapshot? {
        return repository
            .update { state ->
                if (state.config == null || !state.collectionAllowed) return@update state
                val locales = observedLocales ?: state.observedLocales
                val snapshot =
                    deviceInfo.snapshot(state.localeOverride, locales).copy(
                        fcmToken = state.fcmToken,
                        pushSubscribed = state.subscribed,
                        usage = state.usage,
                    )
                val changed = state.snapshot?.copy(revision = 0) != snapshot
                val revision = state.revision + if (changed) 1 else 0
                state.copy(observedLocales = locales, revision = revision, snapshot = snapshot.copy(revision = revision))
            }.takeIf { it.collectionAllowed }
            ?.snapshot
    }
}
