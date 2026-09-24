// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.platform

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper

internal class ApplicationObserver(
    private val application: Application,
    private val onResume: (List<String>, Intent?) -> Unit,
    private val onConfigurationChange: () -> Unit,
    private val onForeground: () -> Unit = {},
    private val onBackground: () -> Unit = {},
    private val onHeartbeat: () -> Unit = {},
) : Application.ActivityLifecycleCallbacks,
    ComponentCallbacks {
    private var registered = false
    private var started = 0
    val isForeground: Boolean get() = started > 0
    private val handler = Handler(Looper.getMainLooper())
    private val heartbeat =
        object : Runnable {
            override fun run() {
                if (isForeground) {
                    onHeartbeat()
                    handler.postDelayed(this, 60_000)
                }
            }
        }

    @Synchronized
    fun register() {
        if (registered) return
        application.registerActivityLifecycleCallbacks(this)
        application.registerComponentCallbacks(this)
        registered = true
    }

    override fun onActivityResumed(activity: Activity) {
        onResume(AndroidDeviceInfoProvider.resourceLocales(activity.resources), activity.intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) = onConfigurationChange()

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivityStarted(activity: Activity) {
        if (started++ == 0) {
            onForeground()
            handler.postDelayed(heartbeat, 60_000)
        }
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
        if (started == 0) {
            handler.removeCallbacks(heartbeat)
            onBackground()
        }
    }

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit

    @Deprecated("Android no longer dispatches this callback")
    override fun onLowMemory() = Unit
}
