package com.github.arhor.journey.feature.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.github.arhor.journey.core.navigation.BottomNavDestination
import com.github.arhor.journey.feature.map.R
import kotlinx.serialization.Serializable

@Serializable
data object MapDestination

val mapBottomNavDestination = BottomNavDestination(
    destination = MapDestination,
    labelRes = R.string.map_nav_label,
    icon = Icons.Outlined.Map,
    testTag = "bottomNav:map",
)

fun NavGraphBuilder.mapGraph(snackbarHostState: SnackbarHostState) {
    composable<MapDestination> {
        MapRoute(snackbarHostState = snackbarHostState)
    }
}
