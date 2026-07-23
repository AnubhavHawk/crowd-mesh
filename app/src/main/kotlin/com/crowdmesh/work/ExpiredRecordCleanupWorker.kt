package com.crowdmesh.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crowdmesh.domain.usecase.PruneExpiredRecordsUseCase
import com.crowdmesh.util.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Keeps local storage bounded: drops expired presence records, stale peers, and old dedup-ledger entries. */
@HiltWorker
class ExpiredRecordCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pruneExpiredRecordsUseCase: PruneExpiredRecordsUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = pruneExpiredRecordsUseCase()
        Logger.d(
            TAG,
            "pruned ${result.recordsRemoved} records, ${result.ledgerEntriesRemoved} ledger entries, " +
                "${result.stalePeersRemoved} stale peers",
        )
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "crowdmesh_cleanup"
        private const val TAG = "ExpiredRecordCleanupWorker"
    }
}
