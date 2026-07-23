package com.crowdmesh.domain.usecase

import com.crowdmesh.domain.heatmap.HeatmapAggregator
import com.crowdmesh.domain.model.HeatmapCell
import com.crowdmesh.domain.repository.PresenceRepository
import com.crowdmesh.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Recomputes the local heatmap every time the known-record set changes. */
class ObserveHeatmapUseCase @Inject constructor(
    private val presenceRepository: PresenceRepository,
    private val heatmapAggregator: HeatmapAggregator,
    private val timeProvider: TimeProvider,
) {
    operator fun invoke(cellPrecision: Int = HeatmapAggregator.DEFAULT_CELL_PRECISION): Flow<List<HeatmapCell>> =
        presenceRepository.observeAllRecords().map { records ->
            heatmapAggregator.aggregate(
                records = records,
                nowMillis = timeProvider.nowMillis(),
                cellPrecision = cellPrecision,
            )
        }
}
