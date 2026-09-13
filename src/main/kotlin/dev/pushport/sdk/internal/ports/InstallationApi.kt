// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.ports

import dev.pushport.sdk.PushPortConfig
import dev.pushport.sdk.internal.model.DeviceSnapshot
import dev.pushport.sdk.internal.model.FirebaseConfiguration
import dev.pushport.sdk.internal.model.InstallationIdentity

internal interface InstallationApi {
    fun configuration(packageName: String): FirebaseConfiguration?

    fun register(
        identity: InstallationIdentity,
        snapshot: DeviceSnapshot,
    )

    fun opened(
        identity: InstallationIdentity,
        messageId: String,
    )
}

internal fun interface InstallationApiFactory {
    fun create(config: PushPortConfig): InstallationApi
}

internal class HttpFailure(
    val status: Int,
) : Exception("PushPort API: HTTP $status") {
    val retryable: Boolean get() = status == 408 || status == 409 || status == 429 || status in 500..599
}
