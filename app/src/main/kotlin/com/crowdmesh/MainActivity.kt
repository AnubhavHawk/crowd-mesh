package com.crowdmesh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.crowdmesh.presentation.common.CrowdMeshTheme
import com.crowdmesh.presentation.navigation.CrowdMeshNavGraph
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CrowdMeshTheme {
                CrowdMeshNavGraph()
            }
        }
    }
}
