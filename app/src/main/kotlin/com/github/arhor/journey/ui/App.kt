package com.github.arhor.journey.ui

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.compose.rememberNavController
import com.github.arhor.journey.ui.components.AppBottomBar
import com.github.arhor.journey.ui.theme.AppTheme
import com.github.arhor.journey.ui.navigation.AppNavGraph

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("SnackbarHostState provider is missing")
}

@Composable
fun App() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    AppTheme {
        CompositionLocalProvider(
            LocalSnackbarHostState provides snackbarHostState,
        ) {
            Scaffold(
                bottomBar = { AppBottomBar(navController) },
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { innerPadding ->
                AppNavGraph(
                    navController = navController,
                    innerPadding = innerPadding,
                )
            }
        }
    }
}
