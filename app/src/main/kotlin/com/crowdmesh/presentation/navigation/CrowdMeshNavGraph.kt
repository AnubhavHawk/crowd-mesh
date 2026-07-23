package com.crowdmesh.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.crowdmesh.presentation.home.HomeScreen
import com.crowdmesh.presentation.map.MapScreen

@Composable
fun CrowdMeshNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(onViewMap = { navController.navigate(Screen.Map.route) })
        }
        composable(Screen.Map.route) {
            MapScreen(onBack = { navController.popBackStack() })
        }
    }
}
