package com.crowdmesh.domain.usecase

import com.crowdmesh.domain.model.PresenceRecord
import com.crowdmesh.domain.repository.PresenceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveOwnPresenceUseCase @Inject constructor(
    private val presenceRepository: PresenceRepository,
) {
    operator fun invoke(): Flow<PresenceRecord?> = presenceRepository.observeOwnRecord()
}
