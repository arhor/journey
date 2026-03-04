package com.github.arhor.journey.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.github.arhor.journey.ui.views.home.HomeRoute
import com.github.arhor.journey.ui.views.settings.SettingsRoute

@Composable
fun AppNavGraph(
    navController: NavHostController,
    innerPadding: PaddingValues,
) {
    NavHost(
        navController = navController,
        startDestination = Home,
        modifier = Modifier.padding(innerPadding),
    ) {
        composable<Home> {
            HomeRoute()
        }
        composable<Settings> {
            SettingsRoute()
        }
    }
}
