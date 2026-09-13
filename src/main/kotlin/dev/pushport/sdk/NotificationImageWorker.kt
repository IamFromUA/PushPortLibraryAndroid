// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.pushport.sdk.internal.model.PushProtocol
import dev.pushport.sdk.internal.network.NotificationImageDownloader
import dev.pushport.sdk.internal.runtime.SdkRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adds an optional image after the text notification is visible; never retries or recreates dismissed notifications.
 * @suppress Android entry point, not an application integration API.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class NotificationImageWorker public constructor(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val id = inputData.getString(PushProtocol.MESSAGE_ID) ?: return@withContext Result.failure()
            val appId = inputData.getString(PushProtocol.APP_ID) ?: return@withContext Result.failure()
            val url = inputData.getString(PushProtocol.IMAGE_URL) ?: return@withContext Result.failure()
            val runtime = SdkRuntime.from(applicationContext)
            if (!runtime.canEnrichNotification(appId, id)) return@withContext Result.success()
            val image = NotificationImageDownloader().download(url) ?: return@withContext Result.success()
            if (!isStopped) runtime.enrichNotification(appId, id, image)
            Result.success()
        }
}
