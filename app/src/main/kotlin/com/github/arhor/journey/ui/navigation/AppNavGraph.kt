package com.github.arhor.journey.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.github.arhor.journey.feature.home.HomeRoute
import com.github.arhor.journey.feature.map.MapRoute
import com.github.arhor.journey.feature.settings.SettingsRoute

@Composable
fun AppNavGraph(
    navController: NavHostController,
    innerPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
) {
    NavHost(
        navController = navController,
        startDestination = Home,
        modifier = Modifier.padding(innerPadding),
    ) {
        composable<Home> {
            HomeRoute(snackbarHostState = snackbarHostState)
        }
        composable<Map> {
            MapRoute(snackbarHostState = snackbarHostState)
        }
        composable<Settings> {
            SettingsRoute(snackbarHostState = snackbarHostState)
        }
    }
}
