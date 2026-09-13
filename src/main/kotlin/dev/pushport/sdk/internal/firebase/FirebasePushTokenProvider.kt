// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import dev.pushport.sdk.internal.model.FirebaseConfiguration
import dev.pushport.sdk.internal.ports.PushTokenProvider
import kotlinx.coroutines.tasks.await

internal class FirebasePushTokenProvider(
    context: Context,
) : PushTokenProvider {
    private val context = context.applicationContext

    override fun restore(configuration: FirebaseConfiguration) {
        application(configuration)
    }

    // Keep the existing token registration mode; switching to FID registration is a separate migration.
    @Suppress("DEPRECATION")
    override suspend fun token(configuration: FirebaseConfiguration): String =
        application(configuration).get(FirebaseMessaging::class.java).token.await()

    @Synchronized
    internal fun application(configuration: FirebaseConfiguration): FirebaseApp {
        val options =
            FirebaseOptions
                .Builder()
                .setApplicationId(configuration.applicationId)
                .setApiKey(configuration.apiKey)
                .setGcmSenderId(configuration.senderId)
                .setProjectId(configuration.projectId)
                .build()
        val existing = FirebaseApp.getApps(context).firstOrNull { it.name == FIREBASE_APP_NAME }
        if (existing != null) {
            if (existing.options == options) return existing
            existing.delete()
        }
        return FirebaseApp.initializeApp(context, options, FIREBASE_APP_NAME)
    }

    private companion object {
        const val FIREBASE_APP_NAME = "PUSHPORT_FCM"
    }
}
