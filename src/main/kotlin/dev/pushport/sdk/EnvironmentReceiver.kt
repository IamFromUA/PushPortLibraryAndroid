// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.RestrictTo
import dev.pushport.sdk.internal.runtime.SdkRuntime

/**
 * Schedules metadata refreshes after Android locale, timezone or application updates.
 * @suppress Android entry point, not an application integration API.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class EnvironmentReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in ACTIONS) return
        SdkRuntime.from(context).controller.environmentChanged(intent.action == Intent.ACTION_LOCALE_CHANGED)
    }

    private companion object {
        val ACTIONS = setOf(Intent.ACTION_LOCALE_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
