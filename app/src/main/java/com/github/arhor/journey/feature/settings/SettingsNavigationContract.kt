package com.github.arhor.journey.feature.settings

import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object SettingsDestination

fun NavGraphBuilder.settingsGraph(snackbarHostState: SnackbarHostState) {
    composable<SettingsDestination> {
        SettingsRoute(snackbarHostState = snackbarHostState)
    }
}
