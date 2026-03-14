package com.github.arhor.journey.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.github.arhor.journey.core.navigation.BottomNavDestination
import com.github.arhor.journey.feature.settings.R
import kotlinx.serialization.Serializable

@Serializable
data object SettingsDestination

val settingsBottomNavDestination = BottomNavDestination(
    destination = SettingsDestination,
    labelRes = R.string.settings_nav_label,
    icon = Icons.Outlined.Settings,
    testTag = "bottomNav:settings",
)

fun NavGraphBuilder.settingsGraph(snackbarHostState: SnackbarHostState) {
    composable<SettingsDestination> {
        SettingsRoute(snackbarHostState = snackbarHostState)
    }
}
