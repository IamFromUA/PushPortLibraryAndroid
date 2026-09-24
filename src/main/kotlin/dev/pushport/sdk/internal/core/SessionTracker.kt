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
) {
    private var checkpoint: Long? = null

    @Synchronized
    fun foreground() {
        if (checkpoint != null) return
        val state = repository.read()
        if (state.config == null || !state.collectionAllowed) return
        val now = wallMillis()
        repository.update {
            val usage = it.usage
            val gap = now - it.lastActivityAt
            val fresh = usage.sessionCount == 0L || gap < 0 || gap >= 30_000
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
        checkpoint()
        checkpoint = null
    }
}
