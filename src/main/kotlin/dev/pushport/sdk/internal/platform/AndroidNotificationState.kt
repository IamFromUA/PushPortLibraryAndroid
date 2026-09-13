// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.platform

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import dev.pushport.sdk.internal.model.PushProtocol
import dev.pushport.sdk.internal.ports.NotificationStateProvider

internal class AndroidNotificationState(
    private val context: Context,
) : NotificationStateProvider {
    override fun isEnabled(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(PushProtocol.CHANNEL)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    companion object {
        fun request(
            activity: Activity,
            requestCode: Int,
        ) {
            if (Build.VERSION.SDK_INT >= 33) {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestCode)
            }
        }
    }
}
