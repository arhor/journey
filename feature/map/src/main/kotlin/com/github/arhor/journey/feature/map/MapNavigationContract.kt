package com.github.arhor.journey.feature.map

import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object MapDestination

@Serializable
data class PoiDetailsDestination(
    val poiId: String,
)

@Serializable
data class AddPoiDestination(
    val initialLatitude: Double,
    val initialLongitude: Double,
)

fun NavGraphBuilder.mapGraph(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    onOpenHero: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMiniGame: () -> Unit,
) {
    composable<MapDestination> {
        MapRoute(
            snackbarHostState = snackbarHostState,
            onOpenHero = onOpenHero,
            onOpenSettings = onOpenSettings,
            onOpenObjectDetails = { poiId ->
                navController.navigate(PoiDetailsDestination(poiId = poiId))
            },
            onOpenAddPoi = { latitude, longitude ->
                navController.navigate(
                    AddPoiDestination(
                        initialLatitude = latitude,
                        initialLongitude = longitude,
                    ),
                )
            },
        )
    }

    composable<PoiDetailsDestination> {
        PoiDetailsRoute(
            onBack = navController::navigateUp,
            onOpenMiniGame = onOpenMiniGame,
        )
    }

    composable<AddPoiDestination> {
        AddPoiRoute(
            snackbarHostState = snackbarHostState,
            onBack = navController::navigateUp,
        )
    }
}
