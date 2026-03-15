package com.github.arhor.journey.feature.map

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MapRoute(
    vm: MapViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        vm.dispatch(MapIntent.StartLocationTracking)

        onDispose {
            vm.dispatch(MapIntent.StopLocationTracking)
        }
    }

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is MapEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }

                is MapEffect.OpenObjectDetails -> {
                    snackbarHostState.showSnackbar("Open details for ${effect.objectId}")
                }
            }
        }
    }

    MapScreen(
        state = state,
        dispatch = vm::dispatch,
    )
}
