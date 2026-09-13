// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.model

import dev.pushport.sdk.PushPortConfig

internal data class InstallationIdentity(
    val id: String,
    val secret: String,
) {
    override fun toString(): String = "InstallationIdentity(id=$id, secret=<redacted>)"
}

internal data class FirebaseConfiguration(
    val applicationId: String,
    val apiKey: String,
    val senderId: String,
    val projectId: String,
)

internal data class DeviceSnapshot(
    val revision: Long = 0,
    val fcmToken: String? = null,
    val packageName: String,
    val language: String,
    val locale: String,
    val appLocales: List<String>,
    val systemLocales: List<String>,
    val timezone: String,
    val notificationsEnabled: Boolean,
    val pushSubscribed: Boolean = true,
    val sdkVersion: String,
    val appVersion: String,
    val appVersionCode: Long,
    val osVersion: String,
    val androidApi: Int,
    val manufacturer: String,
    val model: String,
    val androidId: String? = null,
) {
    override fun toString(): String = "DeviceSnapshot(revision=$revision, packageName=$packageName, locale=$locale, fcmToken=<redacted>)"
}

/** Immutable state; JSON is confined to the persistence and HTTP adapters. */
internal data class InstallationState(
    val config: PushPortConfig? = null,
    val identity: InstallationIdentity? = null,
    val subscribed: Boolean = true,
    val localeOverride: String? = null,
    val observedLocales: List<String>? = null,
    val firebase: FirebaseConfiguration? = null,
    val fcmToken: String? = null,
    val snapshot: DeviceSnapshot? = null,
    val revision: Long = 0,
    val lastSyncedRevision: Long = 0,
    val lastSyncedAt: Long? = null,
    val syncError: String? = null,
    val pushError: String? = null,
    val pendingOpenedMessages: List<String> = emptyList(),
    val receivedMessages: List<String> = emptyList(),
) {
    override fun toString(): String = "InstallationState(identity=$identity, revision=$revision)"
}
