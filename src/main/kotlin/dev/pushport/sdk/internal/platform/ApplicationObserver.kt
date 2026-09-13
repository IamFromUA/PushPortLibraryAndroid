// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.platform

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle

internal class ApplicationObserver(
    private val application: Application,
    private val onResume: (List<String>, Intent?) -> Unit,
    private val onConfigurationChange: () -> Unit,
) : Application.ActivityLifecycleCallbacks,
    ComponentCallbacks {
    private var registered = false

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

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit

    @Deprecated("Android no longer dispatches this callback")
    override fun onLowMemory() = Unit
}
