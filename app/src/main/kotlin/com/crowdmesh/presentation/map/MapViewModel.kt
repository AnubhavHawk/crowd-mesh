package com.crowdmesh.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crowdmesh.domain.model.HeatmapCell
import com.crowdmesh.domain.model.PresenceRecord
import com.crowdmesh.domain.usecase.ObserveHeatmapUseCase
import com.crowdmesh.domain.usecase.ObserveOwnPresenceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    observeHeatmapUseCase: ObserveHeatmapUseCase,
    observeOwnPresenceUseCase: ObserveOwnPresenceUseCase,
) : ViewModel() {

    val heatmapCells: StateFlow<List<HeatmapCell>> = observeHeatmapUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    /** The device's own presence record (geohash precision 8), so the map can mark "you are here". */
    val ownRecord: StateFlow<PresenceRecord?> = observeOwnPresenceUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null,
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
