// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.storage

import dev.pushport.sdk.PushPortConfig
import dev.pushport.sdk.internal.model.InstallationIdentity
import dev.pushport.sdk.internal.model.InstallationState
import dev.pushport.sdk.internal.serialization.UserJson
import dev.pushport.sdk.internal.serialization.WireJson
import dev.pushport.sdk.internal.serialization.stringOrNull
import dev.pushport.sdk.internal.serialization.strings
import org.json.JSONArray
import org.json.JSONObject

/** Reads the original unversioned format without rotating identity or discarding pending events. */
internal class InstallationStateJson {
    fun decode(value: String): InstallationState {
        val json = JSONObject(value)
        require(json.optInt("schemaVersion", SCHEMA_VERSION) == SCHEMA_VERSION) { "Unsupported installation schema" }
        val config =
            json.optJSONObject("config")?.let {
                PushPortConfig(it.getString("appId"), it.getString("serverUrl"), it.optBoolean("allowInsecureLocalhost"))
            }
        val identity = json.stringOrNull("installationId")?.let { InstallationIdentity(it, json.getString("secret")) }
        require(config == null || identity != null || json.optBoolean("consentRequired")) { "Stored installation identity is missing" }
        return InstallationState(
            config = config,
            identity = identity,
            subscribed = json.optBoolean("subscribed", true),
            localeOverride = json.stringOrNull("localeOverride"),
            observedLocales = json.optJSONArray("observedLocales")?.strings(),
            firebase = json.optJSONObject("firebase")?.let(WireJson::firebase),
            fcmToken = json.stringOrNull("fcmToken"),
            snapshot = json.optJSONObject("snapshot")?.let(WireJson::snapshot),
            revision = json.optLong("revision"),
            lastSyncedRevision = json.optLong("lastSyncedRevision"),
            lastSyncedAt = json.optLong("lastSyncedAt").takeIf { it > 0 },
            syncError = json.stringOrNull("syncError"),
            pushError = json.stringOrNull("pushError"),
            pendingOpenedMessages = json.optJSONArray("events")?.strings().orEmpty(),
            receivedMessages = json.optJSONArray("receivedMessages")?.strings().orEmpty(),
            usage =
                json.optJSONObject("usage")?.let(WireJson::usage) ?: dev.pushport.sdk.internal.model
                    .UsageSnapshot(),
            lastActivityAt = json.optLong("lastActivityAt"),
            user = json.optJSONObject("user")?.let(UserJson::profile),
            nextUserRevision = json.optLong("nextUserRevision"),
            pendingUserOperations =
                json
                    .optJSONArray("userOperations")
                    ?.let { a ->
                        (0 until a.length()).map { UserJson.operation(a.getJSONObject(it)) }
                    }.orEmpty(),
            consentRequired = json.optBoolean("consentRequired"),
            consentGiven = json.optBoolean("consentGiven"),
        )
    }

    fun encode(state: InstallationState): String {
        val json =
            JSONObject()
                .put("schemaVersion", SCHEMA_VERSION)
                .put("subscribed", state.subscribed)
                .put("localeOverride", state.localeOverride)
                .put("observedLocales", state.observedLocales?.let(::JSONArray))
                .put("firebase", state.firebase?.let(WireJson::firebase))
                .put("fcmToken", state.fcmToken)
                .put("snapshot", state.snapshot?.let(WireJson::snapshot))
                .put("revision", state.revision)
                .put("lastSyncedRevision", state.lastSyncedRevision)
                .put("lastSyncedAt", state.lastSyncedAt)
                .put("syncError", state.syncError)
                .put("pushError", state.pushError)
                .put("events", JSONArray(state.pendingOpenedMessages))
                .put("receivedMessages", JSONArray(state.receivedMessages))
                .put("usage", WireJson.usage(state.usage))
                .put("lastActivityAt", state.lastActivityAt)
                .put("user", state.user?.let(UserJson::profile))
                .put("nextUserRevision", state.nextUserRevision)
                .put("userOperations", JSONArray(state.pendingUserOperations.map(UserJson::operation)))
                .put("consentRequired", state.consentRequired)
                .put("consentGiven", state.consentGiven)
        state.identity?.let { json.put("installationId", it.id).put("secret", it.secret) }
        state.config?.let {
            json.put(
                "config",
                JSONObject()
                    .put("appId", it.appId)
                    .put("serverUrl", it.serverUrl)
                    .put("allowInsecureLocalhost", it.allowInsecureLocalhost),
            )
        }
        return json.toString()
    }

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}
