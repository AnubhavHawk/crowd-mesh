package com.crowdmesh.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crowdmesh.domain.repository.MeshController
import com.crowdmesh.domain.usecase.ObserveKnownRecordCountUseCase
import com.crowdmesh.domain.usecase.ObserveMeshStatusUseCase
import com.crowdmesh.domain.usecase.ObserveOwnPresenceUseCase
import com.crowdmesh.domain.usecase.UpdatePresenceUseCase
import com.crowdmesh.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val updatePresenceUseCase: UpdatePresenceUseCase,
    observeOwnPresenceUseCase: ObserveOwnPresenceUseCase,
    observeMeshStatusUseCase: ObserveMeshStatusUseCase,
    observeKnownRecordCountUseCase: ObserveKnownRecordCountUseCase,
    private val meshController: MeshController,
) : ViewModel() {

    private val isUpdating = MutableStateFlow(false)

    private val _errors = MutableSharedFlow<HomeError>(extraBufferCapacity = 1)
    val errors: SharedFlow<HomeError> = _errors.asSharedFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        observeOwnPresenceUseCase(),
        observeMeshStatusUseCase(),
        observeKnownRecordCountUseCase(),
        isUpdating,
    ) { ownRecord, meshStatus, knownRecordCount, updating ->
        HomeUiState(
            ownRecord = ownRecord,
            meshStatus = meshStatus,
            isUpdating = updating,
            knownRecordCount = knownRecordCount,
        )
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HomeUiState(),
    )

    /** Called once permissions are confirmed granted (see [com.crowdmesh.presentation.permissions.PermissionsGate]). */
    fun onPermissionsGranted() {
        meshController.start()
    }

    fun onUpdateTapped() {
        if (isUpdating.value) return
        viewModelScope.launch {
            isUpdating.value = true
            when (val result = updatePresenceUseCase()) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> when (result.reason) {
                    UpdatePresenceUseCase.Failure.LOCATION_UNAVAILABLE ->
                        _errors.tryEmit(HomeError.LOCATION_UNAVAILABLE)
                }
            }
            isUpdating.value = false
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
