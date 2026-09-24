// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.core

import dev.pushport.sdk.internal.model.PushProtocol
import dev.pushport.sdk.internal.model.isCanonicalUuid
import dev.pushport.sdk.internal.ports.InstallationRepository
import dev.pushport.sdk.internal.ports.SyncScheduler

/** Keeps acknowledgement tracking independent of notification presentation. */
internal class NotificationOpenTracker(
    private val repository: InstallationRepository,
    private val scheduler: SyncScheduler,
) {
    fun opened(messageId: String) {
        if (!isCanonicalUuid(messageId) || repository.read().config == null || !repository.read().collectionAllowed) return
        repository.update { state ->
            if (!state.collectionAllowed || messageId in state.pendingOpenedMessages) {
                state
            } else {
                state.copy(
                    pendingOpenedMessages = (state.pendingOpenedMessages + messageId).takeLast(PushProtocol.MAX_PENDING_MESSAGES),
                )
            }
        }
        scheduler.enqueue()
    }
}
