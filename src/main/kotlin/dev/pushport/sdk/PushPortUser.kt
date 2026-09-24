// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

/** Last server-confirmed user profile. Null before first registration; changes are asynchronous.
 * @property userId PushPort user UUID, shared by linked installations in this app.
 * @property externalId Customer-defined account ID, or null for an anonymous user.
 * @property tags Explicitly supplied user attributes.
 * @property email Explicitly supplied email, not an email delivery subscription.
 * @property phoneNumber Explicitly supplied E.164 phone, not an SMS subscription.
 * @property latitude Last explicitly supplied latitude; no automatic GPS collection.
 * @property longitude Last explicitly supplied longitude.
 * @property revision Last applied user operation for this installation.
 */
public data class PushPortUser(
    val userId: String,
    val externalId: String?,
    val tags: Map<String, String>,
    val email: String?,
    val phoneNumber: String?,
    val latitude: Double?,
    val longitude: Double?,
    val revision: Long,
) {
    /** Returns identifiers/revision only; contact data, tags and coordinates are redacted. */
    override fun toString(): String = "PushPortUser(userId=$userId, revision=$revision, properties=<redacted>)"
}
