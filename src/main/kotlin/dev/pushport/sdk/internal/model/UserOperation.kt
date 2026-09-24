// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.model

internal data class UserOperation(
    val revision: Long = 0,
    val kind: String,
    val externalId: String? = null,
    val identityToken: String? = null,
    val tags: Map<String, String?> = emptyMap(),
    val email: String? = null,
    val phoneNumber: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val eventName: String? = null,
    val eventProperties: Map<String, String> = emptyMap(),
) {
    fun validate() {
        require(kind in setOf("login", "logout", "tags", "email", "phone", "location", "event"))
        if (kind == "login") {
            require(
                !externalId.isNullOrBlank() && externalId.length <= 128 && externalId == externalId.trim() &&
                    externalId.none(Char::isISOControl),
            ) { "Invalid external ID" }
            require(identityToken?.matches(Regex("[A-Za-z0-9_-]{43}")) == true) { "Identity token is required" }
        }
        require(
            tags.size <= 50 &&
                tags.all { (k, v) ->
                    k.isNotBlank() && k.length <= 64 && k.none(Char::isISOControl) && (v?.length ?: 0) <= 256
                },
        ) { "Invalid tags" }
        require(email == null || (email.length <= 254 && email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))) { "Invalid email" }
        require(phoneNumber == null || phoneNumber.matches(Regex("\\+[1-9][0-9]{6,14}"))) { "Phone must be E.164" }
        require(
            ((latitude == null) == (longitude == null)) && (latitude == null || (latitude.isFinite() && latitude in -90.0..90.0)) &&
                (longitude == null || (longitude.isFinite() && longitude in -180.0..180.0)),
        ) { "Invalid coordinates" }
        if (kind == "event") {
            require(
                eventName?.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")) == true && eventProperties.size <= 20 &&
                    eventProperties.all { (k, v) ->
                        k.isNotBlank() && k.length <= 64 && k.none(Char::isISOControl) && v.length <= 256
                    },
            ) { "Invalid event" }
        }
    }

    override fun toString(): String = "UserOperation(revision=$revision, kind=$kind, data=<redacted>)"
}
