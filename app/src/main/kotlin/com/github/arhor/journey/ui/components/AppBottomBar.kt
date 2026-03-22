package com.github.arhor.journey.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.github.arhor.journey.feature.hero.HeroBottomNavDestination
import com.github.arhor.journey.feature.map.mapBottomNavDestination
import com.github.arhor.journey.feature.settings.settingsBottomNavDestination
import com.github.arhor.journey.ui.navigation.navigateToTopLevel

val bottomNavDestinations = listOf(
    HeroBottomNavDestination,
    mapBottomNavDestination,
    settingsBottomNavDestination,
)

@Composable
fun AppBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val graphHierarchy = backStackEntry?.destination?.hierarchy ?: emptySequence()
    val isBottomBarDestination = bottomNavDestinations.any { destination ->
        graphHierarchy.any { it.hasRoute(destination.destination::class) }
    }

    if (!isBottomBarDestination) {
        return
    }

    NavigationBar {
        bottomNavDestinations.forEach { destination ->
            val label = stringResource(destination.labelRes)

            NavigationBarItem(
                modifier = Modifier.testTag(destination.testTag),
                selected = graphHierarchy.any { it.hasRoute(destination.destination::class) },
                onClick = {
                    navController.navigateToTopLevel(destination.destination)
                },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = label,
                    )
                },
                label = { Text(label) },
            )
        }
    }
}
