// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

import java.net.URI
import java.util.UUID

/** Connection settings for one installation; prefer [PushPort.initWithContext] for standard integration. */
public data class PushPortConfig
    @JvmOverloads
    public constructor(
        /** Application UUID issued by the PushPort backend. */
        public val appId: String,
        /** Absolute HTTPS API endpoint, with no credentials, query or fragment. */
        public val serverUrl: String,
        /** Only honored in debuggable applications. Production always requires HTTPS. */
        public val allowInsecureLocalhost: Boolean = false,
    ) {
        internal fun validate(debuggable: Boolean): PushPortConfig {
            require(
                runCatching { UUID.fromString(appId).toString() == appId }.getOrDefault(false),
            ) { "Use the application UUID from PushPort" }
            val uri = runCatching { URI(serverUrl) }.getOrNull()
            require(
                uri != null && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null &&
                    (uri.port == -1 || uri.port in 1..65535),
            ) {
                "Use an absolute server URL without credentials, query or fragment"
            }
            val local = uri.host in setOf("localhost", "127.0.0.1", "10.0.2.2", "[::1]")
            require(uri.scheme == "https" || (uri.scheme == "http" && allowInsecureLocalhost && debuggable && local)) {
                "HTTPS is required (HTTP is available only for local debug builds)"
            }
            return copy(serverUrl = serverUrl.trimEnd('/'))
        }
    }
