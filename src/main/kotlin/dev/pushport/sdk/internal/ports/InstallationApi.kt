// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.ports

import dev.pushport.sdk.PushPortConfig
import dev.pushport.sdk.internal.model.DeviceSnapshot
import dev.pushport.sdk.internal.model.FirebaseConfiguration
import dev.pushport.sdk.internal.model.InstallationIdentity

internal interface InstallationApi {
    fun configuration(packageName: String): FirebaseConfiguration?

    fun settings(packageName: String): dev.pushport.sdk.internal.model.RemoteConfiguration =
        dev.pushport.sdk.internal.model
            .RemoteConfiguration(configuration(packageName))

    fun register(
        identity: InstallationIdentity,
        snapshot: DeviceSnapshot,
    )

    fun registerAndReadUser(
        identity: InstallationIdentity,
        snapshot: DeviceSnapshot,
    ): dev.pushport.sdk.PushPortUser? {
        register(identity, snapshot)
        return null
    }

    fun updateUser(
        identity: InstallationIdentity,
        operation: dev.pushport.sdk.internal.model.UserOperation,
    ): dev.pushport.sdk.PushPortUser = error("User API unavailable")

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
