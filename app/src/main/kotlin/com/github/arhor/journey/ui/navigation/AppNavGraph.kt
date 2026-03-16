package com.github.arhor.journey.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.github.arhor.journey.feature.home.HomeDestination
import com.github.arhor.journey.feature.home.homeGraph
import com.github.arhor.journey.feature.map.mapGraph
import com.github.arhor.journey.feature.settings.settingsGraph

@Composable
fun AppNavGraph(
    navController: NavHostController,
    innerPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
) {
    NavHost(
        navController = navController,
        startDestination = HomeDestination,
        modifier = Modifier.padding(innerPadding),
    ) {
        homeGraph(snackbarHostState = snackbarHostState)
        mapGraph(
            navController = navController,
            snackbarHostState = snackbarHostState,
        )
        settingsGraph(snackbarHostState = snackbarHostState)
    }
}
