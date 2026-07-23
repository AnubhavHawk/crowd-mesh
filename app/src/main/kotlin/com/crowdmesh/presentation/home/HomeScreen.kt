package com.crowdmesh.presentation.home

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crowdmesh.R
import com.crowdmesh.presentation.common.MeshStatusBadge
import com.crowdmesh.presentation.common.PulsingUpdateButton
import com.crowdmesh.presentation.permissions.PermissionsGate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onViewMap: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val locationUnavailableMessage = stringResource(R.string.home_error_location)
    LaunchedEffect(Unit) {
        viewModel.errors.collect { error ->
            val message = when (error) {
                HomeError.LOCATION_UNAVAILABLE -> locationUnavailableMessage
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    PermissionsGate(onAllGranted = viewModel::onPermissionsGranted) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(stringResource(R.string.home_title)) }) },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            HomeContent(
                uiState = uiState,
                padding = padding,
                onUpdateTapped = viewModel::onUpdateTapped,
                onViewMap = onViewMap,
            )
        }
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    padding: PaddingValues,
    onUpdateTapped: () -> Unit,
    onViewMap: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MeshStatusBadge(status = uiState.meshStatus)
        Spacer(Modifier.height(32.dp))

        PulsingUpdateButton(
            isUpdating = uiState.isUpdating,
            onClick = onUpdateTapped,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (uiState.isUpdating) {
                stringResource(R.string.home_updating)
            } else {
                stringResource(R.string.home_update_button)
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = lastUpdatedLabel(uiState),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(40.dp))
        Text(
            text = stringResource(R.string.home_mesh_records, uiState.knownRecordCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onViewMap, modifier = Modifier.fillMaxWidth(0.8f)) {
            Text(stringResource(R.string.home_view_map))
        }
    }
}

@Composable
private fun lastUpdatedLabel(uiState: HomeUiState): String {
    val record = uiState.ownRecord ?: return stringResource(R.string.home_never_updated)
    val relative = DateUtils.getRelativeTimeSpanString(
        record.timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    )
    return stringResource(R.string.home_last_updated, relative)
}
