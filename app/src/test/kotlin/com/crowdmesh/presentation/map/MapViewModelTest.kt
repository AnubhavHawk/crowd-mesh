package com.crowdmesh.presentation.map

import app.cash.turbine.test
import com.crowdmesh.MainDispatcherRule
import com.crowdmesh.data.repository.PresenceRepositoryImpl
import com.crowdmesh.domain.heatmap.HeatmapAggregator
import com.crowdmesh.domain.model.PresenceRecord
import com.crowdmesh.domain.usecase.ObserveHeatmapUseCase
import com.crowdmesh.fakes.FakeIdentityProvider
import com.crowdmesh.fakes.FakePresenceRecordDao
import com.crowdmesh.fakes.FakeTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val timeProvider = FakeTimeProvider()
    private val repository = PresenceRepositoryImpl(FakePresenceRecordDao(), FakeIdentityProvider(), timeProvider)

    private fun buildViewModel(): MapViewModel =
        MapViewModel(ObserveHeatmapUseCase(repository, HeatmapAggregator(), timeProvider))

    @Test
    fun `starts with an empty heatmap`() = runTest {
        val viewModel = buildViewModel()
        viewModel.heatmapCells.test {
            assertTrue(awaitItem().isEmpty())
        }
    }

    @Test
    fun `merging records produces heatmap cells reflecting the new data`() = runTest {
        val viewModel = buildViewModel()
        viewModel.heatmapCells.test {
            assertTrue(awaitItem().isEmpty())

            repository.mergeRemoteRecord(
                PresenceRecord("peer-1", "u4pruyd0", timeProvider.nowMillis(), 1),
                ttlExpiresAtMillis = timeProvider.nowMillis() + 60_000,
            )

            val updated = expectMostRecentItem()
            assertEquals(1, updated.size)
            assertEquals(1, updated.first().userCount)
        }
    }
}
