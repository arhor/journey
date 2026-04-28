package com.github.arhor.journey.feature.settings

import androidx.compose.runtime.Stable
import com.github.arhor.journey.core.ui.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@Stable
@HiltViewModel
class SettingsViewModel @Inject constructor(
) : MviViewModel<SettingsUiState, SettingsEffect, SettingsIntent>(
    initialState = SettingsUiState.Content,
) {
    override fun buildUiState(): Flow<SettingsUiState> =
        flowOf(SettingsUiState.Content)

    override suspend fun handleIntent(intent: SettingsIntent) {
        // Intentionally empty: settings currently has no writable controls.
    }
}
