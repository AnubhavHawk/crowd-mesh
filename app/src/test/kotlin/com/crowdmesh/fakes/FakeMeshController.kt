package com.crowdmesh.fakes

import com.crowdmesh.domain.model.MeshStatus
import com.crowdmesh.domain.repository.MeshController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeMeshController(initialStatus: MeshStatus = MeshStatus()) : MeshController {
    private val _status = MutableStateFlow(initialStatus)
    override val status: StateFlow<MeshStatus> = _status

    var startCallCount = 0
        private set
    var stopCallCount = 0
        private set
    var notifyCallCount = 0
        private set

    fun emitStatus(status: MeshStatus) {
        _status.value = status
    }

    override fun notifyLocalRecordChanged() {
        notifyCallCount++
    }

    override fun start() {
        startCallCount++
    }

    override fun stop() {
        stopCallCount++
    }
}
