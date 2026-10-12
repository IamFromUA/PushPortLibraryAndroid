// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.model

import java.util.UUID

internal data class PushMessage(
    val appId: String,
    val id: String,
    val title: String,
    val body: String,
    val imageUrl: String? = null,
    val clickUrl: String? = null,
    val largeIcon: String? = null,
    val smallIcon: String? = null,
)

internal fun isCanonicalUuid(value: String): Boolean = runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)

/** Persistent names and wire keys must remain compatible across SDK updates. */
internal object PushProtocol {
    const val CHANNEL = "pushport_default"
    const val MESSAGE_ID = "pushport_message_id"
    const val APP_ID = "pushport_app_id"
    const val IMAGE_URL = "image_url"
    const val CLICK_URL = "click_url"
    const val LARGE_ICON = "large_icon"
    const val SMALL_ICON = "small_icon"
    const val IMAGE_KIND = "pushport_image_kind"
    const val RECEIVE_ACTION = "com.google.android.c2dm.intent.RECEIVE"
    const val MAX_PENDING_MESSAGES = 100
}
