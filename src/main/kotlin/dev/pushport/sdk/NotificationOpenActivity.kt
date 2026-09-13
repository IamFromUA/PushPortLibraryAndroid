// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.annotation.RestrictTo

/**
 * Internal PendingIntent target; host activities need no onNewIntent integration.
 * @suppress Android entry point, not an application integration API.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class NotificationOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        open(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open(intent)
    }

    private fun open(intent: Intent) {
        PushPort.handleNotificationIntent(this, intent)
        val url =
            dev.pushport.sdk.internal.model.publicHttpsUrl(
                intent.getStringExtra(dev.pushport.sdk.internal.model.PushProtocol.CLICK_URL),
            )
        if (url != null) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
                finish()
                return
            } catch (_: android.content.ActivityNotFoundException) {
                // Fall back to the host application.
            }
        }
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(it)
        }
        finish()
    }
}
