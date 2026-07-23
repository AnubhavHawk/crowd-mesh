package com.crowdmesh.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.crowdmesh.R
import com.crowdmesh.domain.model.MeshActivity
import com.crowdmesh.domain.model.MeshStatus

@Composable
fun MeshStatusBadge(status: MeshStatus, modifier: Modifier = Modifier) {
    val (dotColor, activityText) = when (status.activity) {
        MeshActivity.IDLE -> MaterialTheme.colorScheme.outline to stringResource(R.string.mesh_status_idle)
        MeshActivity.SCANNING -> MaterialTheme.colorScheme.primary to stringResource(R.string.mesh_status_scanning)
        MeshActivity.SYNCING -> DensityGreen to stringResource(R.string.mesh_status_syncing, status.syncingPeerCount)
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = dotColor, shape = CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(text = activityText, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.home_mesh_peers, status.nearbyPeerCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
