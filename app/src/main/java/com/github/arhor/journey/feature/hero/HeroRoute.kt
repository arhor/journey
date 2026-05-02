package com.github.arhor.journey.feature.hero

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HeroRoute(
    vm: HeroViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.effects.collect {
            when (it) {
                is HeroEffect.Error -> snackbarHostState.showSnackbar(it.message)
                is HeroEffect.Success -> snackbarHostState.showSnackbar(it.message)
            }
        }
    }

    HeroScreen(
        state = state,
        dispatch = vm::dispatch,
    )
}
