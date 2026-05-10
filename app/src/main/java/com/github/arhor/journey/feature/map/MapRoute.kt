package com.github.arhor.journey.feature.map

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

@Composable
fun MapRoute(
    vm: MapViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState,
    onOpenHero: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        launch(start = CoroutineStart.UNDISPATCHED) {
            vm.effects.collect { effect ->
                when (effect) {
                    is MapEffect.ShowMessage -> {
                        snackbarHostState.showSnackbar(effect.message)
                    }

                    MapEffect.RequestLocationPermission -> Unit
                }
            }
        }

        vm.dispatch(MapIntent.MapOpened)
    }

    MapScreen(
        state = state,
        dispatch = vm::dispatch,
    )
}
