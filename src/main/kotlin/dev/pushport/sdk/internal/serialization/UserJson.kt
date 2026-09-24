// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.serialization

import dev.pushport.sdk.PushPortUser
import dev.pushport.sdk.internal.model.UserOperation
import org.json.JSONObject

internal object UserJson {
    fun profile(json: JSONObject): PushPortUser {
        val properties = json.getJSONObject("properties")
        return PushPortUser(
            json.getString("userId"),
            json.stringOrNull("externalId"),
            properties.optJSONObject("tags")?.stringMap().orEmpty(),
            properties.stringOrNull("email"),
            properties.stringOrNull("phoneNumber"),
            properties.doubleOrNull("latitude"),
            properties.doubleOrNull("longitude"),
            json.optLong("revision"),
        )
    }

    fun profile(user: PushPortUser): JSONObject =
        JSONObject()
            .put("userId", user.userId)
            .put("externalId", user.externalId)
            .put("revision", user.revision)
            .put(
                "properties",
                JSONObject()
                    .put(
                        "tags",
                        JSONObject(user.tags),
                    ).put("email", user.email)
                    .put("phoneNumber", user.phoneNumber)
                    .put("latitude", user.latitude)
                    .put("longitude", user.longitude),
            )

    fun operation(value: UserOperation): JSONObject =
        JSONObject()
            .put(
                "revision",
                value.revision,
            ).put("kind", value.kind)
            .put("externalId", value.externalId)
            .put("identityToken", value.identityToken)
            .put("tags", JSONObject().apply { value.tags.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } })
            .put(
                "email",
                value.email ?: JSONObject.NULL,
            ).put("phoneNumber", value.phoneNumber ?: JSONObject.NULL)
            .put(
                "latitude",
                value.latitude ?: JSONObject.NULL,
            ).put(
                "longitude",
                value.longitude ?: JSONObject.NULL,
            ).put("eventName", value.eventName)
            .put("eventProperties", JSONObject(value.eventProperties))

    fun operation(json: JSONObject): UserOperation =
        UserOperation(
            json.getLong(
                "revision",
            ),
            json.getString(
                "kind",
            ),
            json.stringOrNull(
                "externalId",
            ),
            json.stringOrNull("identityToken"),
            json
                .optJSONObject("tags")
                ?.let { tags ->
                    tags.keys().asSequence().associateWith {
                        if (tags.isNull(it)) null else tags.getString(it)
                    }
                }.orEmpty(),
            json.stringOrNull(
                "email",
            ),
            json.stringOrNull(
                "phoneNumber",
            ),
            json.doubleOrNull(
                "latitude",
            ),
            json.doubleOrNull("longitude"),
            json.stringOrNull("eventName"),
            json.optJSONObject("eventProperties")?.stringMap().orEmpty(),
        )

    private fun JSONObject.stringMap(): Map<String, String> = keys().asSequence().associateWith(::getString)

    private fun JSONObject.doubleOrNull(key: String): Double? = if (isNull(key)) null else getDouble(key)
}
