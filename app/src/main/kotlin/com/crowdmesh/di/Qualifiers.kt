package com.crowdmesh.di

import javax.inject.Qualifier

/**
 * A process-lifetime [kotlinx.coroutines.CoroutineScope] for the mesh
 * subsystem's background collectors (GATT server callbacks, discovery
 * loops). Deliberately never cancelled while the app process is alive —
 * see [MeshModule].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
