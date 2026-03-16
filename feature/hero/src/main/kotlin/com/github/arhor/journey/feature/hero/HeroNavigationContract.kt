package com.github.arhor.journey.feature.hero

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.github.arhor.journey.core.navigation.BottomNavDestination
import kotlinx.serialization.Serializable

@Serializable
data object HeroDestination

val HeroBottomNavDestination = BottomNavDestination(
    destination = HeroDestination,
    labelRes = R.string.hero_nav_label,
    icon = Icons.Outlined.Groups,
    testTag = "bottomNav:hero",
)

fun NavGraphBuilder.heroGraph(snackbarHostState: SnackbarHostState) {
    composable<HeroDestination> {
        HeroRoute(snackbarHostState = snackbarHostState)
    }
}
