// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.network

import dev.pushport.sdk.PushPortConfig
import dev.pushport.sdk.internal.ports.HttpFailure
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

internal class UrlConnectionTransport(
    private val config: PushPortConfig,
) {
    fun request(
        method: String,
        path: String,
        secret: String? = null,
        body: String? = null,
    ): String {
        val url = URL("${config.serverUrl}/api/v1/sdk/apps/${config.appId}$path")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            // An installation credential must never be forwarded to a redirect target.
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "PushPort-Android")
            secret?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            body?.let {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { output -> output.write(it.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw HttpFailure(status)
            return connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    require(output.size() + count <= MAX_RESPONSE_BYTES) { "PushPort response too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray().toString(Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000
        const val MAX_RESPONSE_BYTES = 65_536
    }
}
