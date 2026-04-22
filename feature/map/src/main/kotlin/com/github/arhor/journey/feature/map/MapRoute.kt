package com.github.arhor.journey.feature.map

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MapRoute(
    vm: MapViewModel = hiltViewModel(),
    hudVm: MapHudViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState,
    onOpenHero: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val hudState by hudVm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is MapEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }

                MapEffect.RequestLocationPermission -> Unit
            }
        }
    }

    MapScreen(
        state = state,
        hudState = hudState,
        dispatch = vm::dispatch,
        onOpenHero = onOpenHero,
        onOpenSettings = onOpenSettings,
    )
}
