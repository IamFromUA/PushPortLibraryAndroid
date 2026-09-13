// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

/** A local status snapshot; a non-null token does not by itself confirm delivery of a notification. */
public data class PushPortStatus(
    /** Whether connection settings have been saved. */
    public val configured: Boolean,
    /** Stable per-installation ID; not a hardware identifier. */
    public val installationId: String?,
    /** Current FCM registration token. Treat as device-specific data and avoid logging it. */
    public val fcmToken: String?,
    /** Effective BCP-47 application locale, including any explicit SDK override. */
    public val locale: String?,
    /** Ordered system locales reported by Android. */
    public val systemLocales: List<String>,
    /** Whether Android permits notifications for this application and the PushPort channel. */
    public val notificationsEnabled: Boolean,
    /** Application-level subscription setting, independent of Android permission. */
    public val subscribed: Boolean,
    /** Last successful device registration, in Unix epoch milliseconds; null before first success. */
    public val lastSyncedAt: Long?,
    /** Human-readable synchronization diagnostic, or null when no error is recorded. */
    public val syncError: String?,
    /** Human-readable Firebase diagnostic, or null when no error is recorded. */
    public val pushError: String?,
) {
    /** Safe diagnostic representation; the registration token is deliberately omitted. */
    override fun toString(): String =
        "PushPortStatus(configured=$configured, installationId=$installationId, " +
            "hasFcmToken=${fcmToken != null}, locale=$locale, systemLocales=$systemLocales, " +
            "notificationsEnabled=$notificationsEnabled, subscribed=$subscribed, " +
            "lastSyncedAt=$lastSyncedAt, syncError=$syncError, pushError=$pushError)"
}
