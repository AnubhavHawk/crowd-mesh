package com.crowdmesh.presentation.home

import com.crowdmesh.domain.model.MeshStatus
import com.crowdmesh.domain.model.PresenceRecord

data class HomeUiState(
    val ownRecord: PresenceRecord? = null,
    val meshStatus: MeshStatus = MeshStatus(),
    val isUpdating: Boolean = false,
    val knownRecordCount: Int = 0,
)

enum class HomeError {
    LOCATION_UNAVAILABLE,
}
