package com.github.arhor.journey.feature.map

import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object MapDestination

fun NavGraphBuilder.mapGraph(
    snackbarHostState: SnackbarHostState,
) {
    composable<MapDestination> {
        MapRoute(
            snackbarHostState = snackbarHostState,
        )
    }
}
