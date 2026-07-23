package com.crowdmesh.domain.repository

import com.crowdmesh.domain.model.MeshStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * The port domain use-cases use to drive the mesh engine, implemented by
 * [com.crowdmesh.mesh.MeshEngine]. Keeps `domain` free of any dependency on
 * the `mesh` package (mesh depends inward on domain, never the reverse).
 */
interface MeshController {

    val status: StateFlow<MeshStatus>

    /** Called after the local presence record changes so the mesh can wake up and advertise it. */
    fun notifyLocalRecordChanged()

    /** Starts background discovery/advertising/sync. Safe to call repeatedly. */
    fun start()

    fun stop()
}
