// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.network

import dev.pushport.sdk.internal.model.RemoteConfiguration
import java.util.concurrent.TimeUnit

/** Shares the launch GET with concurrent workers for the same immutable installation binding. */
internal class StartupSettings(
    private val now: () -> Long = System::nanoTime,
) {
    private var cached: RemoteConfiguration? = null
    private var cachedPackage: String? = null

    @Volatile
    private var startedAt: Long? = null

    fun begin() {
        startedAt = now()
    }

    @Synchronized
    fun read(
        packageName: String,
        fetch: () -> RemoteConfiguration,
    ): RemoteConfiguration {
        val start = startedAt ?: return fetch()
        if (now() - start >= TimeUnit.SECONDS.toNanos(10)) return fetch()
        cached?.let { if (packageName == cachedPackage) return it }
        return fetch().also {
            cached = it
            cachedPackage = packageName
        }
    }
}
