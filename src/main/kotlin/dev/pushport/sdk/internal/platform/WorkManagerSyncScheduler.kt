// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk.internal.platform

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.pushport.sdk.SyncWorker
import dev.pushport.sdk.internal.ports.SyncScheduler
import java.util.concurrent.TimeUnit

internal class WorkManagerSyncScheduler(
    private val context: Context,
) : SyncScheduler {
    override fun enqueue() {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork("pushport-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    override fun schedulePeriodic() {
        val request =
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints())
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("pushport-periodic", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun constraints(): Constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
}
