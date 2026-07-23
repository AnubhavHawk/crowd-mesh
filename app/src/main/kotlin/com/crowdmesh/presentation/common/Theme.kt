package com.crowdmesh.presentation.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = CrowdMeshPrimary,
    background = CrowdMeshSurfaceLight,
    surface = CrowdMeshSurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = CrowdMeshPrimaryDark,
    background = CrowdMeshSurfaceDark,
    surface = CrowdMeshSurfaceDark,
)

@Composable
fun CrowdMeshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CrowdMeshTypography,
        content = content,
    )
}
