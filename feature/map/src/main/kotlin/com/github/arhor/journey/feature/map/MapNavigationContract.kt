package com.github.arhor.journey.feature.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.github.arhor.journey.core.navigation.BottomNavDestination
import com.github.arhor.journey.feature.map.R
import kotlinx.serialization.Serializable

@Serializable
data object MapDestination

@Serializable
data class PoiDetailsDestination(
    val poiId: String,
)

val mapBottomNavDestination = BottomNavDestination(
    destination = MapDestination,
    labelRes = R.string.map_nav_label,
    icon = Icons.Outlined.Map,
    testTag = "bottomNav:map",
)

fun NavGraphBuilder.mapGraph(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
) {
    composable<MapDestination> {
        MapRoute(
            snackbarHostState = snackbarHostState,
            onOpenObjectDetails = { poiId ->
                navController.navigate(PoiDetailsDestination(poiId = poiId))
            },
        )
    }

    composable<PoiDetailsDestination> {
        PoiDetailsRoute(
            onBack = navController::navigateUp,
        )
    }
}
