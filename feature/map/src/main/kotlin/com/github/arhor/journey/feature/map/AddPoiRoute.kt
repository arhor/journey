package com.github.arhor.journey.feature.map

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AddPoiRoute(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    vm: AddPoiViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                AddPoiEffect.Saved -> onBack()
                is AddPoiEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    AddPoiScreen(
        state = state,
        onNameChanged = vm::onNameChanged,
        onDescriptionChanged = vm::onDescriptionChanged,
        onCategorySelected = vm::onCategorySelected,
        onRadiusChanged = vm::onRadiusChanged,
        onLatitudeChanged = vm::onLatitudeChanged,
        onLongitudeChanged = vm::onLongitudeChanged,
        onSaveClicked = vm::save,
        onBack = onBack,
    )
}
