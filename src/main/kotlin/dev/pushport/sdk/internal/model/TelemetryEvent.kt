// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0
package dev.pushport.sdk.internal.model

/** Bounded diagnostics: never contains notification text, URLs, tokens or exception messages. */
internal data class TelemetryEvent(
    val id: String,
    val type: String,
    val occurredAt: Long,
    val messageId: String? = null,
    val reason: String? = null,
    val durationMillis: Long? = null,
    val sizeBytes: Long? = null,
    val httpStatus: Int? = null,
)

internal const val DEFAULT_IMAGE_BYTES: Int = 5 * 1024 * 1024
internal const val MAX_IMAGE_BYTES: Int = 20 * 1024 * 1024
internal const val MAX_TELEMETRY_EVENTS: Int = 1000
