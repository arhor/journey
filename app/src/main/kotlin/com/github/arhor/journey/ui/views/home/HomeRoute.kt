package com.github.arhor.journey.ui.views.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.arhor.journey.ui.LocalSnackbarHostState

@Composable
fun HomeRoute(
    vm: HomeViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = LocalSnackbarHostState.current,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.effects.collect {
            when (it) {
                is HomeEffect.Error -> snackbarHostState.showSnackbar(it.message)
            }
        }
    }

    HomeScreen(
        state = state,
        dispatch = vm::dispatch,
    )
}
