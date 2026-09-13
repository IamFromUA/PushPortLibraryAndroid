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
    override fun configuration(packageName: String): FirebaseConfiguration? {
        val encoded = URLEncoder.encode(packageName, "UTF-8")
        val response = transport.request("GET", "/config?packageName=$encoded")
        val document = JSONObject(response)
        require(document.has("firebase")) { "Missing Firebase configuration field" }
        return if (document.isNull("firebase")) null else WireJson.firebase(document.getJSONObject("firebase"))
    }

    override fun register(
        identity: InstallationIdentity,
        snapshot: DeviceSnapshot,
    ) {
        transport.request("PUT", "/installations/${identity.id}", identity.secret, WireJson.snapshot(snapshot).toString())
    }

    override fun opened(
        identity: InstallationIdentity,
        messageId: String,
    ) {
        val body = JSONObject().put("messageId", messageId).put("type", "opened").toString()
        transport.request("POST", "/installations/${identity.id}/events", identity.secret, body)
    }
}
