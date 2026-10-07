// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.platform

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.pushport.sdk.NotificationImageWorker
import dev.pushport.sdk.NotificationOpenActivity
import dev.pushport.sdk.R
import dev.pushport.sdk.internal.core.TelemetryTracker
import dev.pushport.sdk.internal.model.PushMessage
import dev.pushport.sdk.internal.model.PushProtocol
import dev.pushport.sdk.internal.ports.NotificationPresenter
import dev.pushport.sdk.internal.ports.NotificationStateProvider

internal class AndroidNotificationPresenter(
    private val context: Context,
    private val permissions: NotificationStateProvider,
    private val telemetry: TelemetryTracker? = null,
) : NotificationPresenter {
    override fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel =
                NotificationChannel(
                    PushProtocol.CHANNEL,
                    context.getString(R.string.pushport_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    override fun show(message: PushMessage) {
        createChannel()
        if (!permissions.isEnabled()) {
            telemetry?.record("not_displayed", message.id, "permission_disabled")
            return
        }
        if (Build.VERSION.SDK_INT >= 26 && context
                .getSystemService(NotificationManager::class.java)
                .getNotificationChannel(PushProtocol.CHANNEL)
                ?.importance == NotificationManager.IMPORTANCE_NONE
        ) {
            telemetry?.record("not_displayed", message.id, "channel_disabled")
            return
        }
        val launch =
            Intent(context, NotificationOpenActivity::class.java)
                .setAction("${context.packageName}.PUSHPORT.${message.id}")
                .putExtra(PushProtocol.MESSAGE_ID, message.id)
                .putExtra(PushProtocol.CLICK_URL, message.clickUrl)
        val pending =
            PendingIntent.getActivity(
                context,
                message.id.hashCode(),
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, PushProtocol.CHANNEL)
                .setSmallIcon(R.drawable.pushport_notification)
                .setContentTitle(message.title)
                .setContentText(message.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
        try {
            NotificationManagerCompat.from(context).notify(message.id, 0, notification)
        } catch (_: SecurityException) {
            telemetry?.record("not_displayed", message.id, "permission_disabled")
            return
        } catch (_: RuntimeException) {
            telemetry?.record("not_displayed", message.id, "notification_error")
            return
        }
        telemetry?.record("posted", message.id)
        try {
            if (message.imageUrl != null) {
                val work =
                    OneTimeWorkRequestBuilder<NotificationImageWorker>()
                        .setInputData(
                            workDataOf(
                                PushProtocol.APP_ID to message.appId,
                                PushProtocol.MESSAGE_ID to message.id,
                                PushProtocol.IMAGE_URL to message.imageUrl,
                            ),
                        ).build()
                WorkManager.getInstance(context).enqueueUniqueWork("pushport-image-${message.id}", ExistingWorkPolicy.KEEP, work)
            }
        } catch (_: RuntimeException) {
            telemetry?.record("image_skipped", message.id, "worker_unavailable")
        }
    }

    fun isVisible(id: String): Boolean =
        context.getSystemService(NotificationManager::class.java).activeNotifications.any {
            it.tag == id &&
                it.id == 0
        }

    @SuppressLint("MissingPermission")
    fun enrich(
        id: String,
        image: Bitmap,
    ) {
        if (!permissions.isEnabled()) {
            telemetry?.record("image_skipped", id, "permission_disabled")
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val current = manager.activeNotifications.firstOrNull { it.tag == id && it.id == 0 }
        if (current == null) {
            telemetry?.record("image_skipped", id, "notification_unavailable")
            return
        }
        val notification =
            NotificationCompat
                .Builder(context, current.notification)
                .setStyle(
                    NotificationCompat
                        .BigPictureStyle()
                        .bigPicture(
                            image,
                        ).setSummaryText(current.notification.extras.getCharSequence("android.text")),
                ).setOnlyAlertOnce(true)
                .build()
        try {
            manager.notify(id, 0, notification)
            telemetry?.record("image_attached", id)
        } catch (_: SecurityException) {
            telemetry?.record("image_skipped", id, "permission_disabled")
        } catch (_: RuntimeException) {
            telemetry?.record("image_skipped", id, "notification_error")
        }
    }
}
