package com.crowdmesh.domain.usecase

import com.crowdmesh.domain.geohash.GeohashEncoder
import com.crowdmesh.domain.model.PresenceRecord
import com.crowdmesh.domain.repository.IdentityProvider
import com.crowdmesh.domain.repository.LocationProvider
import com.crowdmesh.domain.repository.MeshController
import com.crowdmesh.domain.repository.PresenceRepository
import com.crowdmesh.util.AppResult
import com.crowdmesh.util.CoroutineDispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The entire "press the button" golden path: read GPS once, turn it into a
 * geohash, replace the user's single owned [PresenceRecord] locally, and
 * wake the mesh so it can start advertising the change to nearby peers.
 */
class UpdatePresenceUseCase @Inject constructor(
    private val locationProvider: LocationProvider,
    private val identityProvider: IdentityProvider,
    private val presenceRepository: PresenceRepository,
    private val meshController: MeshController,
    private val dispatchers: CoroutineDispatchers,
) {
    enum class Failure { LOCATION_UNAVAILABLE }

    suspend operator fun invoke(): AppResult<PresenceRecord, Failure> = withContext(dispatchers.io) {
        val fix = locationProvider.getCurrentFix()
            ?: return@withContext AppResult.Failure(Failure.LOCATION_UNAVAILABLE)

        val geohash = GeohashEncoder.encode(fix.latitude, fix.longitude)
        val userId = identityProvider.getOrCreateDeviceId()
        val record = presenceRepository.upsertOwnRecord(
            userId = userId,
            geohash = geohash,
            timestampMillis = fix.timestampMillis,
        )

        meshController.notifyLocalRecordChanged()

        AppResult.Success(record)
    }
}
