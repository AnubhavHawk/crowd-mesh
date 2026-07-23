package com.crowdmesh.domain.usecase

import com.crowdmesh.domain.repository.PresenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Total non-expired presence records currently known (own + everyone gossiped to us). */
class ObserveKnownRecordCountUseCase @Inject constructor(
    private val presenceRepository: PresenceRepository,
) {
    operator fun invoke(): Flow<Int> = presenceRepository.observeAllRecords().map { it.size }
}
