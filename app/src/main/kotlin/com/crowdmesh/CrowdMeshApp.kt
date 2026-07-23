package com.crowdmesh

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.crowdmesh.work.MeshWorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Note what's absent here: no analytics init, no crash-reporter init, no
 * network client, no auth SDK. CrowdMeshApp's only startup jobs are wiring
 * Hilt's WorkManager factory and scheduling the two background workers —
 * the mesh engine itself only starts once permissions are confirmed (see
 * `presentation.home.HomeViewModel`), not eagerly at process start, since
 * starting it before permissions exist would just silently do nothing.
 */
@HiltAndroidApp
class CrowdMeshApp : Application(), Configuration.Provider {

    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory

    @Inject lateinit var meshWorkScheduler: MeshWorkScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        meshWorkScheduler.scheduleAll()
    }
}
