package com.crowdmesh.domain.usecase

import com.crowdmesh.domain.model.MeshStatus
import com.crowdmesh.domain.repository.MeshController
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveMeshStatusUseCase @Inject constructor(
    private val meshController: MeshController,
) {
    operator fun invoke(): StateFlow<MeshStatus> = meshController.status
}
