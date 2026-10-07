// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.core

import dev.pushport.sdk.internal.model.PushMessage
import dev.pushport.sdk.internal.model.PushProtocol
import dev.pushport.sdk.internal.model.isCanonicalUuid
import dev.pushport.sdk.internal.ports.InstallationRepository
import dev.pushport.sdk.internal.ports.NotificationPresenter

internal class PushHandler(
    private val repository: InstallationRepository,
    private val presenter: NotificationPresenter,
    private val telemetry: TelemetryTracker? = null,
) {
    fun accept(message: PushMessage): Boolean {
        if (!isCanonicalUuid(message.id)) return false
        var owned = false
        var accepted = false
        var subscribed = false
        repository.update { state ->
            if (state.config?.appId != message.appId) return@update state
            owned = true
            if (!state.collectionAllowed) return@update state
            if (message.id in state.receivedMessages) return@update state
            accepted = true
            subscribed = state.subscribed
            state.copy(receivedMessages = (state.receivedMessages + message.id).takeLast(PushProtocol.MAX_PENDING_MESSAGES))
        }
        if (accepted) {
            telemetry?.record("received", message.id)
            if (subscribed) presenter.show(message) else telemetry?.record("not_displayed", message.id, "unsubscribed")
        }
        return owned
    }
}
