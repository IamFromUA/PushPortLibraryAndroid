// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.serialization

import dev.pushport.sdk.internal.model.DeviceSnapshot
import dev.pushport.sdk.internal.model.FirebaseConfiguration
import org.json.JSONArray
import org.json.JSONObject

internal object WireJson {
    fun firebase(json: JSONObject): FirebaseConfiguration =
        FirebaseConfiguration(
            applicationId = json.getString("applicationId"),
            apiKey = json.getString("apiKey"),
            senderId = json.getString("senderId"),
            projectId = json.getString("projectId"),
        )

    fun firebase(config: FirebaseConfiguration): JSONObject =
        JSONObject()
            .put("applicationId", config.applicationId)
            .put("apiKey", config.apiKey)
            .put("senderId", config.senderId)
            .put("projectId", config.projectId)

    fun snapshot(value: DeviceSnapshot): JSONObject =
        JSONObject()
            .put("revision", value.revision)
            .put("fcmToken", value.fcmToken ?: JSONObject.NULL)
            .put("packageName", value.packageName)
            .put("language", value.language)
            .put("locale", value.locale)
            .put("appLocales", JSONArray(value.appLocales))
            .put("systemLocales", JSONArray(value.systemLocales))
            .put("timezone", value.timezone)
            .put("notificationsEnabled", value.notificationsEnabled)
            .put("pushSubscribed", value.pushSubscribed)
            .put("sdkVersion", value.sdkVersion)
            .put("appVersion", value.appVersion)
            .put("appVersionCode", value.appVersionCode)
            .put("osVersion", value.osVersion)
            .put("androidApi", value.androidApi)
            .put("manufacturer", value.manufacturer)
            .put("model", value.model)
            .put("androidId", value.androidId ?: JSONObject.NULL)

    fun snapshot(json: JSONObject): DeviceSnapshot =
        DeviceSnapshot(
            revision = json.getLong("revision"),
            fcmToken = json.stringOrNull("fcmToken"),
            packageName = json.getString("packageName"),
            language = json.getString("language"),
            locale = json.getString("locale"),
            appLocales = json.getJSONArray("appLocales").strings(),
            systemLocales = json.getJSONArray("systemLocales").strings(),
            timezone = json.getString("timezone"),
            notificationsEnabled = json.getBoolean("notificationsEnabled"),
            pushSubscribed = json.optBoolean("pushSubscribed", true),
            sdkVersion = json.getString("sdkVersion"),
            appVersion = json.getString("appVersion"),
            appVersionCode = json.getLong("appVersionCode"),
            osVersion = json.getString("osVersion"),
            androidApi = json.getInt("androidApi"),
            manufacturer = json.getString("manufacturer"),
            model = json.getString("model"),
            androidId = json.stringOrNull("androidId"),
        )
}

internal fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

internal fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)
