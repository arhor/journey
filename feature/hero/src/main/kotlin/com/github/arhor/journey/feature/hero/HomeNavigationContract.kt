package com.github.arhor.journey.feature.hero

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.github.arhor.journey.core.navigation.BottomNavDestination
import kotlinx.serialization.Serializable

@Serializable
data object HomeDestination

val homeBottomNavDestination = BottomNavDestination(
    destination = HomeDestination,
    labelRes = R.string.home_nav_label,
    icon = Icons.Outlined.Groups,
    testTag = "bottomNav:home",
)

fun NavGraphBuilder.homeGraph(snackbarHostState: SnackbarHostState) {
    composable<HomeDestination> {
        HomeRoute(snackbarHostState = snackbarHostState)
    }
}
