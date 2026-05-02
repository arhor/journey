package com.github.arhor.journey.feature.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsRoute(
    vm: SettingsViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.effects.collect {
            when (it) {
                is SettingsEffect.Error -> snackbarHostState.showSnackbar(it.message)
                is SettingsEffect.Success -> snackbarHostState.showSnackbar(it.message)
            }
        }
    }

    SettingsScreen(
        state = state,
        dispatch = vm::dispatch,
    )
}
