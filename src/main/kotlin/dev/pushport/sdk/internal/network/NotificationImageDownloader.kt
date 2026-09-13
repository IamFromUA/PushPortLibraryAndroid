// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.pushport.sdk.internal.model.publicHttpsUrl
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** Pins connections to public DNS answers; bounds time, bytes and decoded pixels. No cookies, credentials or redirects. */
internal class NotificationImageDownloader {
    fun download(value: String): Bitmap? {
        val url = publicHttpsUrl(value) ?: return null
        return runCatching {
            client
                .newCall(
                    Request
                        .Builder()
                        .url(url)
                        .header("Accept", "image/png,image/jpeg,image/webp")
                        .build(),
                ).execute()
                .use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body
                    if (body.contentLength() > MAX_BYTES || body.contentType()?.type != "image") return@use null
                    val bytes =
                        body.byteStream().use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count == -1) break
                                if (output.size() + count > MAX_BYTES) return@use null
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        } ?: return@use null
                    decode(bytes)
                }
        }.getOrNull()
    }

    internal fun decode(bytes: ByteArray): Bitmap? {
        if (bytes.size > MAX_BYTES) return null
        if (bytes.size < 12) return null
        val png = bytes.take(8) == listOf(137, 80, 78, 71, 13, 10, 26, 10).map(Int::toByte)
        val jpeg = bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
        val webp =
            bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" && bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
        if (!png && !jpeg && !webp) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096 ||
            bounds.outWidth.toLong() * bounds.outHeight > 8_000_000
        ) {
            return null
        }
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = 1
                while (bounds.outWidth / inSampleSize > 1024 ||
                    bounds.outHeight / inSampleSize > 1024
                ) {
                    inSampleSize *= 2
                }
            }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    companion object {
        private const val MAX_BYTES = 1_048_576

        internal fun isPublic(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress ||
                address.isMulticastAddress
            ) {
                return false
            }
            val bytes = address.address.map { it.toInt() and 255 }
            return when (bytes.size) {
                4 -> {
                    bytes[0] !in setOf(0, 10, 127) && !(bytes[0] == 100 && bytes[1] in 64..127) &&
                        !(bytes[0] == 169 && bytes[1] == 254) && !(bytes[0] == 192 && bytes[1] == 168) &&
                        !(bytes[0] == 172 && bytes[1] in 16..31) && bytes[0] < 224
                }

                16 -> {
                    bytes[0] and 0xfe != 0xfc && bytes[0] and 0xe0 == 0x20
                }

                else -> {
                    false
                }
            }
        }

        private val client =
            OkHttpClient
                .Builder()
                .dns(
                    object : Dns {
                        override fun lookup(hostname: String): List<InetAddress> =
                            Dns.SYSTEM.lookup(hostname).also { addresses ->
                                if (addresses.isEmpty() ||
                                    addresses.any { !isPublic(it) }
                                ) {
                                    throw UnknownHostException("Non-public image host")
                                }
                            }
                    },
                ).proxy(Proxy.NO_PROXY)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(10, TimeUnit.SECONDS)
                .build()
    }
}
