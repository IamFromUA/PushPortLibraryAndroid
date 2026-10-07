// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0
package dev.pushport.sdk.internal.core

import dev.pushport.sdk.internal.model.MAX_TELEMETRY_EVENTS
import dev.pushport.sdk.internal.model.TelemetryEvent
import dev.pushport.sdk.internal.ports.InstallationRepository
import dev.pushport.sdk.internal.ports.SyncScheduler
import java.util.UUID

/** Durable, consent-aware outbox. Reports do not initiate a network call on the notification thread. */
internal class TelemetryTracker(
    private val repository: InstallationRepository,
    private val scheduler: SyncScheduler,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun record(
        type: String,
        messageId: String? = null,
        reason: String? = null,
        durationMillis: Long? = null,
        sizeBytes: Long? = null,
        httpStatus: Int? = null,
        schedule: Boolean = true,
    ) {
        var queued = false
        repository.update { state ->
            if (!state.collectionAllowed || state.config == null || state.identity == null) return@update state
            // Do not duplicate a per-message milestone or repeatedly store the same sync failure.
            if (state.pendingTelemetry.any {
                    it.type == type && it.messageId == messageId && it.reason == reason &&
                        (
                            (
                                messageId != null &&
                                    !type.startsWith(
                                        "session_",
                                    )
                            ) || (type == "sync_failed" && now() - it.occurredAt < 3_600_000)
                        )
                }
            ) {
                return@update state
            }
            queued = state.pendingTelemetry.isEmpty()
            val event = TelemetryEvent(UUID.randomUUID().toString(), type, now(), messageId, reason, durationMillis, sizeBytes, httpStatus)
            state.copy(
                pendingTelemetry = (state.pendingTelemetry + event).takeLast(MAX_TELEMETRY_EVENTS),
                telemetryDropped = state.telemetryDropped + if (state.pendingTelemetry.size >= MAX_TELEMETRY_EVENTS) 1 else 0,
                lastReceivedMessageId = if (type == "received") messageId else state.lastReceivedMessageId,
                lastReceivedAt = if (type == "received") event.occurredAt else state.lastReceivedAt,
                lastClickedMessageId = if (type == "clicked") messageId else state.lastClickedMessageId,
                lastClickedAt = if (type == "clicked") event.occurredAt else state.lastClickedAt,
            )
        }
        if (queued && schedule) runCatching { scheduler.enqueue() }
    }
}
