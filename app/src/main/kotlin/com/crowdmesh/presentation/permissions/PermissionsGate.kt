package com.crowdmesh.presentation.permissions

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.crowdmesh.R

/**
 * Gates [content] behind the mesh's runtime permissions, requesting them
 * once on first composition and calling [onAllGranted] the moment they're
 * confirmed — that's the app's only "wake networking" trigger tied to
 * permissions (see `HomeViewModel.onPermissionsGranted`).
 */
@Composable
fun PermissionsGate(
    onAllGranted: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val requiredPermissions = remember { RequiredPermissions.forCurrentApi() }

    var checked by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        checked = true
        granted = results.values.all { it }
        if (granted) onAllGranted()
    }

    LaunchedEffect(Unit) {
        val alreadyGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        checked = true
        if (alreadyGranted) {
            granted = true
            onAllGranted()
        } else {
            launcher.launch(requiredPermissions.toTypedArray())
        }
    }

    when {
        !checked -> Unit
        granted -> content()
        else -> PermissionsRationale(onRetry = { launcher.launch(requiredPermissions.toTypedArray()) })
    }
}

@Composable
private fun PermissionsRationale(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.permissions_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.permissions_rationale), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text("• " + stringResource(R.string.permissions_location), style = MaterialTheme.typography.bodySmall)
        Text("• " + stringResource(R.string.permissions_bluetooth), style = MaterialTheme.typography.bodySmall)
        Text("• " + stringResource(R.string.permissions_wifi), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.permissions_grant)) }
    }
}
