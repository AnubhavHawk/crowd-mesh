package com.crowdmesh.presentation.home

import app.cash.turbine.test
import com.crowdmesh.MainDispatcherRule
import com.crowdmesh.data.repository.PresenceRepositoryImpl
import com.crowdmesh.domain.model.MeshActivity
import com.crowdmesh.domain.model.MeshStatus
import com.crowdmesh.domain.usecase.ObserveKnownRecordCountUseCase
import com.crowdmesh.domain.usecase.ObserveMeshStatusUseCase
import com.crowdmesh.domain.usecase.ObserveOwnPresenceUseCase
import com.crowdmesh.domain.usecase.UpdatePresenceUseCase
import com.crowdmesh.fakes.FakeIdentityProvider
import com.crowdmesh.fakes.FakeLocationProvider
import com.crowdmesh.fakes.FakeMeshController
import com.crowdmesh.fakes.FakePresenceRecordDao
import com.crowdmesh.fakes.FakeTimeProvider
import com.crowdmesh.util.CoroutineDispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val identityProvider = FakeIdentityProvider("own-device")
    private val locationProvider = FakeLocationProvider()
    private val meshController = FakeMeshController()
    private val dispatchers = UnconfinedTestDispatcher().let {
        CoroutineDispatchers(io = it, default = it, main = it)
    }
    private val repository = PresenceRepositoryImpl(FakePresenceRecordDao(), identityProvider, FakeTimeProvider())

    private fun buildViewModel(): HomeViewModel = HomeViewModel(
        updatePresenceUseCase = UpdatePresenceUseCase(locationProvider, identityProvider, repository, meshController, dispatchers),
        observeOwnPresenceUseCase = ObserveOwnPresenceUseCase(repository),
        observeMeshStatusUseCase = ObserveMeshStatusUseCase(meshController),
        observeKnownRecordCountUseCase = ObserveKnownRecordCountUseCase(repository),
        meshController = meshController,
    )

    @Test
    fun `initial state has no own record and is not updating`() = runTest {
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.ownRecord)
            assertEquals(false, state.isUpdating)
        }
    }

    @Test
    fun `tapping update publishes the new own record`() = runTest {
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            awaitItem() // initial

            viewModel.onUpdateTapped()

            val updated = expectMostRecentItem()
            assertEquals("own-device", updated.ownRecord?.userId)
        }
    }

    @Test
    fun `location failure surfaces an error event without crashing`() = runTest {
        locationProvider.nextFix = null
        val viewModel = buildViewModel()

        viewModel.errors.test {
            viewModel.onUpdateTapped()
            assertEquals(HomeError.LOCATION_UNAVAILABLE, awaitItem())
        }
    }

    @Test
    fun `permissions granted starts the mesh exactly once`() = runTest {
        val viewModel = buildViewModel()
        viewModel.onPermissionsGranted()
        assertEquals(1, meshController.startCallCount)
    }

    @Test
    fun `mesh status changes are reflected in ui state`() = runTest {
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            awaitItem() // initial
            meshController.emitStatus(MeshStatus(activity = MeshActivity.SYNCING, syncingPeerCount = 2))
            val updated = expectMostRecentItem()
            assertEquals(MeshActivity.SYNCING, updated.meshStatus.activity)
            assertEquals(2, updated.meshStatus.syncingPeerCount)
        }
    }
}
