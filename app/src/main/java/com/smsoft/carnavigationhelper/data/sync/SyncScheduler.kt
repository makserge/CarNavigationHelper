package com.smsoft.carnavigationhelper.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

// Automatic sync: runs when the device is on an unmetered network (Wi-Fi) and the add-on answers.
// - At most once an hour. If Wi-Fi comes up after that hour, the sync starts right away.
// - If the add-on is not reachable (SyncWorker retries), the next try is after 15 minutes, so a late
//   add-on start is picked up while still on Wi-Fi.
// WorkManager keeps the schedule across restarts. Called on every app start so the constraint stays current.
object SyncScheduler {
    private const val WORK_NAME = "auto_sync"
    private const val INTERVAL_MINUTES = 60L
    private const val RETRY_MINUTES = 15L

    fun setAutomatic(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, RETRY_MINUTES, TimeUnit.MINUTES)
                .build()
            // UPDATE replaces an older schedule (for example the former 6-hour one on any network)
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        } else {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }
}
