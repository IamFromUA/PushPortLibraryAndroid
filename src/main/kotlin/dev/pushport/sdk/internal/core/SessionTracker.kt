// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0
package dev.pushport.sdk.internal.core

import dev.pushport.sdk.internal.model.UsageSnapshot
import dev.pushport.sdk.internal.ports.InstallationRepository

/** Counts visible app sessions; wall-clock timestamps never determine foreground duration. */
internal class SessionTracker(
    private val repository: InstallationRepository,
    private val wallMillis: () -> Long,
    private val elapsedMillis: () -> Long,
    private val telemetry: TelemetryTracker? = null,
) {
    private var checkpoint: Long? = null
    private var foregroundStart: Long? = null

    @Synchronized
    fun foreground() {
        if (checkpoint != null) return
        val state = repository.read()
        if (state.config == null || !state.collectionAllowed) return
        val now = wallMillis()
        var freshSession = false
        repository.update {
            val usage = it.usage
            val gap = now - it.lastActivityAt
            val fresh = usage.sessionCount == 0L || gap < 0 || gap >= 30_000
            freshSession = fresh
            it.copy(
                usage =
                    UsageSnapshot(
                        firstSessionAt = usage.firstSessionAt ?: now,
                        lastSessionAt = if (fresh) maxOf(now, usage.lastSessionAt ?: now) else usage.lastSessionAt,
                        sessionCount = usage.sessionCount + if (fresh) 1 else 0,
                        totalUsageMillis = usage.totalUsageMillis,
                    ),
                lastActivityAt = now,
            )
        }
        checkpoint = elapsedMillis()
        foregroundStart = checkpoint
        if (freshSession) {
            val direct = state.lastClickedMessageId != null && now - state.lastClickedAt in 0..60_000
            val influenced =
                state.lastReceivedMessageId != null && state.lastReceivedMessageId != state.lastClickedMessageId &&
                    now - state.lastReceivedAt in 0..3_600_000
            telemetry?.record(
                "session_started",
                if (direct) {
                    state.lastClickedMessageId
                } else if (influenced) {
                    state.lastReceivedMessageId
                } else {
                    null
                },
                if (direct) {
                    "direct"
                } else if (influenced) {
                    "influenced"
                } else {
                    "organic"
                },
            )
        }
    }

    @Synchronized
    fun checkpoint() {
        val previous = checkpoint ?: return
        val current = elapsedMillis()
        repository.update {
            if (!it.collectionAllowed) return@update it
            it.copy(
                usage = it.usage.copy(totalUsageMillis = it.usage.totalUsageMillis + (current - previous).coerceAtLeast(0)),
                lastActivityAt = wallMillis(),
            )
        }
        checkpoint = current
    }

    @Synchronized
    fun background() {
        val started = foregroundStart
        checkpoint()
        checkpoint = null
        foregroundStart = null
        if (started != null) telemetry?.record("session_ended", durationMillis = (elapsedMillis() - started).coerceAtLeast(0))
    }
}
