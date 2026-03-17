package com.github.arhor.journey.feature.map

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun MapRoute(
    vm: MapViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState,
    onOpenObjectDetails: (String) -> Unit,
    onOpenAddPoi: (Double, Double) -> Unit,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.dispatch(MapIntent.StartLocationTracking)
                Lifecycle.Event.ON_STOP -> vm.dispatch(MapIntent.StopLocationTracking)
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            vm.dispatch(MapIntent.StartLocationTracking)
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                    onOpenObjectDetails(effect.objectId)
                }

                is MapEffect.OpenAddPoi -> {
                    onOpenAddPoi(effect.latitude, effect.longitude)
                }
            }
        }
    }

    MapScreen(
        state = state,
        dispatch = vm::dispatch,
    )
}
