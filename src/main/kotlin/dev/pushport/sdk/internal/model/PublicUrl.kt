// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.model

import java.net.URI

/** Notification URLs never allow credentials, custom intents, local names or literal IP addresses. */
internal fun publicHttpsUrl(value: String?): String? =
    value?.takeIf { it.length in 1..1000 }?.let { raw ->
        runCatching {
            val uri = URI(raw)
            val host = uri.host?.lowercase(java.util.Locale.ROOT) ?: return@runCatching null
            raw.takeIf {
                uri.scheme == "https" && uri.rawUserInfo == null && uri.port in setOf(-1, 443) &&
                    '.' in host && ':' !in host && host.any(Char::isLetter) &&
                    !host.endsWith(".local") && !host.endsWith(".localhost") && !host.endsWith(".internal") &&
                    !host.endsWith(".test") && host != "localhost"
            }
        }.getOrNull()
    }
