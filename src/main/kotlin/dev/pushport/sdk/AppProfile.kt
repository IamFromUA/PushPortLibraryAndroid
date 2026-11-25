// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

import org.json.JSONObject

/**
 * Public application content selected by the project owner.
 * @property id Stable identifier, including `default` for the built-in profile.
 * @property name Display name chosen in the project console.
 * @property value Public routing value interpreted by the host application.
 */
public data class AppProfile(
    public val id: String = "default",
    public val name: String = "Основной",
    public val value: String = "",
)

internal fun JSONObject?.readAppProfile(): AppProfile {
    if (this == null || optString("id").isBlank() || optString("name").isBlank()) return AppProfile()
    return AppProfile(getString("id"), getString("name"), optString("value"))
}

internal fun AppProfile.toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("value", value)
