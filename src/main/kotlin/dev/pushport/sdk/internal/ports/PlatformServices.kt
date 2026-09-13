// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.ports

import dev.pushport.sdk.internal.model.DeviceSnapshot
import dev.pushport.sdk.internal.model.FirebaseConfiguration
import dev.pushport.sdk.internal.model.PushMessage

internal fun interface DeviceInfoProvider {
    fun snapshot(
        localeOverride: String?,
        observedLocales: List<String>?,
    ): DeviceSnapshot
}

internal interface PushTokenProvider {
    /** Restores cached Firebase options before a receiver processes a cold-start message. */
    fun restore(configuration: FirebaseConfiguration)

    suspend fun token(configuration: FirebaseConfiguration): String
}

internal interface SyncScheduler {
    fun enqueue()

    fun schedulePeriodic()
}

internal interface NotificationPresenter {
    fun createChannel()

    fun show(message: PushMessage)
}

internal fun interface NotificationStateProvider {
    fun isEnabled(): Boolean
}

internal fun interface Clock {
    fun nowMillis(): Long
}
