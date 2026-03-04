package com.github.arhor.journey.ui.views.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.arhor.journey.ui.LocalSnackbarHostState
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SettingsRoute(
    vm: SettingsViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = LocalSnackbarHostState.current,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(vm.effects) {
        vm.effects.collectLatest { effect ->
            when (effect) {
                is SettingsEffect.Error -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    SettingsScreen(
        state = state,
        dispatch = vm::dispatch,
    )
}
