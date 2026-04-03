package com.github.arhor.journey.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.github.arhor.journey.feature.hero.HeroDestination
import com.github.arhor.journey.feature.hero.heroGraph
import com.github.arhor.journey.feature.map.MapDestination
import com.github.arhor.journey.feature.map.mapGraph
import com.github.arhor.journey.feature.settings.SettingsDestination
import com.github.arhor.journey.feature.settings.settingsGraph

@Composable
fun AppNavGraph(
    controller: NavHostController,
    innerPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
    onOpenMiniGame: () -> Unit,
) {
    NavHost(
        navController = controller,
        startDestination = MapDestination,
        modifier = Modifier.padding(innerPadding),
    ) {
        heroGraph(snackbarHostState = snackbarHostState)
        mapGraph(
            navController = controller,
            snackbarHostState = snackbarHostState,
            onOpenHero = { controller.navigateToTopLevel(HeroDestination) },
            onOpenSettings = { controller.navigateToTopLevel(SettingsDestination) },
            onOpenMiniGame = onOpenMiniGame,
        )
        settingsGraph(snackbarHostState = snackbarHostState)
    }
}
