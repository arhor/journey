package com.github.arhor.journey.feature.hero

import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object HeroDestination

fun NavGraphBuilder.heroGraph(snackbarHostState: SnackbarHostState) {
    composable<HeroDestination> {
        HeroRoute(snackbarHostState = snackbarHostState)
    }
}
