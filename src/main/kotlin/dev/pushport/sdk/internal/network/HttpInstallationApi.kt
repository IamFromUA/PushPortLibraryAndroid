// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.network

import dev.pushport.sdk.internal.model.DeviceSnapshot
import dev.pushport.sdk.internal.model.FirebaseConfiguration
import dev.pushport.sdk.internal.model.InstallationIdentity
import dev.pushport.sdk.internal.ports.InstallationApi
import dev.pushport.sdk.internal.serialization.WireJson
import org.json.JSONObject
import java.net.URLEncoder

internal class HttpInstallationApi(
    private val transport: UrlConnectionTransport,
) : InstallationApi {
    override fun configuration(packageName: String): FirebaseConfiguration? = settings(packageName).firebase

    override fun settings(packageName: String): dev.pushport.sdk.internal.model.RemoteConfiguration {
        val encoded = URLEncoder.encode(packageName, "UTF-8")
        val response = transport.request("GET", "/config?packageName=$encoded")
        val document = JSONObject(response)
        require(document.has("firebase")) { "Missing Firebase configuration field" }
        return dev.pushport.sdk.internal.model.RemoteConfiguration(
            if (document.isNull("firebase")) null else WireJson.firebase(document.getJSONObject("firebase")),
        )
    }

    override fun register(
        identity: InstallationIdentity,
        snapshot: DeviceSnapshot,
    ) {
        transport.request("PUT", "/installations/${identity.id}", identity.secret, WireJson.snapshot(snapshot).toString())
    }

    override fun registerAndReadUser(
        identity: InstallationIdentity,
        snapshot: DeviceSnapshot,
    ): dev.pushport.sdk.PushPortUser? {
        val response = transport.request("PUT", "/installations/${identity.id}", identity.secret, WireJson.snapshot(snapshot).toString())
        if (response.isBlank()) return null // Legacy 204 registration responses have no profile.
        val json = JSONObject(response)
        return json.optJSONObject("user")?.let(dev.pushport.sdk.internal.serialization.UserJson::profile)
    }

    override fun updateUser(
        identity: InstallationIdentity,
        operation: dev.pushport.sdk.internal.model.UserOperation,
    ): dev.pushport.sdk.PushPortUser =
        dev.pushport.sdk.internal.serialization.UserJson.profile(
            JSONObject(
                transport.request(
                    "PUT",
                    "/installations/${identity.id}/user",
                    identity.secret,
                    dev.pushport.sdk.internal.serialization.UserJson
                        .operation(operation)
                        .toString(),
                ),
            ),
        )

    override fun opened(
        identity: InstallationIdentity,
        messageId: String,
    ) {
        val body = JSONObject().put("messageId", messageId).put("type", "opened").toString()
        transport.request("POST", "/installations/${identity.id}/events", identity.secret, body)
    }
}
