package com.github.arhor.journey.ui.views.settings

import androidx.compose.runtime.Stable
import com.github.arhor.journey.ui.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@Stable
@HiltViewModel
class SettingsViewModel @Inject constructor() : MviViewModel<SettingsUiState, SettingsEffect, SettingsIntent>(
    initialState = SettingsUiState.Content
) {
    override suspend fun handleIntent(intent: SettingsIntent) {

    }
}
