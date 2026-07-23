package com.crowdmesh.work

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the two background jobs the app ever runs unattended. Both are
 * short, bounded, and constrained to `BatteryNotLow` — this is the entire
 * "periodic background work" story; there is no foreground service and no
 * continuous background radio usage.
 */
@Singleton
class MeshWorkScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun scheduleAll() {
        scheduleSync()
        scheduleCleanup()
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        // 15 minutes is the minimum period WorkManager allows for periodic work.
        val request = PeriodicWorkRequestBuilder<PeriodicMeshSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PeriodicMeshSyncWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun scheduleCleanup() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<ExpiredRecordCleanupWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            ExpiredRecordCleanupWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
