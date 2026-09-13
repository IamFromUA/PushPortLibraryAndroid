// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.pushport.sdk.internal.core.SyncOutcome
import dev.pushport.sdk.internal.runtime.SdkRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android entry point. Its name must remain stable for persisted WorkManager requests from 0.2.0 onward.
 * @suppress Android entry point, not an application integration API.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SyncWorker public constructor(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            when (SdkRuntime.from(applicationContext).synchronizer.synchronize(runAttemptCount)) {
                SyncOutcome.SUCCESS -> Result.success()
                SyncOutcome.RETRY -> Result.retry()
                SyncOutcome.FAILURE -> Result.failure()
            }
        }
}
