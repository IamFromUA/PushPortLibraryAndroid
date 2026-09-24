// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.core

import dev.pushport.sdk.PushPortUser
import dev.pushport.sdk.internal.model.UserOperation
import dev.pushport.sdk.internal.ports.InstallationRepository
import dev.pushport.sdk.internal.ports.SyncScheduler

/** Durable bounded outbox. An explicit account switch discards unsent operations for the previous account. */
internal class UserController(
    private val repository: InstallationRepository,
    private val scheduler: SyncScheduler,
) {
    fun profile(): PushPortUser? = repository.read().user?.let { it.copy(tags = it.tags.toMap()) }

    fun enqueue(operation: UserOperation) {
        operation.validate()
        repository.update { state ->
            require(state.config != null && state.identity != null) { "Initialize PushPort first" }
            check(state.collectionAllowed) { "User consent is required" }
            val pending = if (operation.kind in setOf("login", "logout")) emptyList() else state.pendingUserOperations
            check(pending.size < 100) { "User operation queue is full; wait for synchronization" }
            val revision = maxOf(state.nextUserRevision, state.user?.revision ?: 0) + 1
            state.copy(
                nextUserRevision = revision,
                pendingUserOperations =
                    pending +
                        operation.copy(
                            revision = revision,
                            tags = operation.tags.toMap(),
                            eventProperties = operation.eventProperties.toMap(),
                        ),
                syncError = null,
            )
        }
        scheduler.enqueue()
    }
}
