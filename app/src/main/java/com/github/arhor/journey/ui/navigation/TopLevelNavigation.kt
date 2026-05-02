package com.github.arhor.journey.ui.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

fun <T : Any> NavHostController.navigateToTopLevel(destination: T) {
    navigate(destination) {
        popUpTo(graph.findStartDestination().id) {
            saveState = false
        }
        launchSingleTop = true
        restoreState = false
    }
}
