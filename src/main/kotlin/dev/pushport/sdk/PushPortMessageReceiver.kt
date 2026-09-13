// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.RestrictTo
import dev.pushport.sdk.internal.model.PushMessage
import dev.pushport.sdk.internal.model.PushProtocol
import dev.pushport.sdk.internal.runtime.SdkRuntime

/**
 * Android adapter for PushPort data messages; unrelated FCM messages continue to the host receiver.
 * @suppress Android entry point, not an application integration API.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PushPortMessageReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (accept(context, intent) && isOrderedBroadcast) {
            resultCode = Activity.RESULT_OK
            abortBroadcast()
        }
    }

    internal fun accept(
        context: Context,
        intent: Intent,
    ): Boolean {
        if (intent.action != PushProtocol.RECEIVE_ACTION ||
            intent.getStringExtra("message_type") !in setOf(null, "gcm") ||
            intent.getStringExtra("from") == "google.com/iid"
        ) {
            return false
        }
        val message =
            PushMessage(
                appId = intent.getStringExtra(PushProtocol.APP_ID) ?: return false,
                id = intent.getStringExtra(PushProtocol.MESSAGE_ID) ?: return false,
                title = intent.getStringExtra("title").orEmpty(),
                body = intent.getStringExtra("body").orEmpty(),
                imageUrl =
                    dev.pushport.sdk.internal.model
                        .publicHttpsUrl(intent.getStringExtra(PushProtocol.IMAGE_URL)),
                clickUrl =
                    dev.pushport.sdk.internal.model
                        .publicHttpsUrl(intent.getStringExtra(PushProtocol.CLICK_URL)),
            )
        return SdkRuntime.from(context).pushHandler.accept(message)
    }
}
