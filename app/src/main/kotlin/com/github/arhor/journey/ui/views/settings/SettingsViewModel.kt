package com.github.arhor.journey.ui.views.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.github.arhor.journey.core.logging.LoggerFactory
import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.Resource
import com.github.arhor.journey.domain.usecase.ObserveSettingsUseCase
import com.github.arhor.journey.domain.usecase.SetDistanceUnitUseCase
import com.github.arhor.journey.ui.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@Immutable
private data class State(
    val isUpdating: Boolean = false,
)

@Stable
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val observeSettings: ObserveSettingsUseCase,
    private val setDistanceUnit: SetDistanceUnitUseCase,
    loggerFactory: LoggerFactory,
) : MviViewModel<SettingsUiState, SettingsEffect, SettingsIntent>(
    loggerFactory = loggerFactory,
    initialState = SettingsUiState.Loading,
) {
    private val _state = MutableStateFlow(State())

    override fun buildUiState(): Flow<SettingsUiState> =
        combine(_state, observeSettings(), ::intoUiState)
            .distinctUntilChanged()

    override suspend fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SelectDistanceUnit -> handleSelectDistanceUnit(intent)
        }
    }

    private suspend fun handleSelectDistanceUnit(intent: SettingsIntent.SelectDistanceUnit) {
        if (_state.value.isUpdating) {
            return
        }
        _state.update { it.copy(isUpdating = true) }
        try {
            setDistanceUnit(intent.unit)
        } catch (e: Throwable) {
            emitEffect(
                SettingsEffect.Error(message = e.message ?: "Failed to update distance unit.")
            )
        } finally {
            _state.update { it.copy(isUpdating = false) }
        }
    }

    private fun intoUiState(state: State, settings: Resource<AppSettings>): SettingsUiState {
        return when (settings) {
            is Resource.Loading -> SettingsUiState.Loading

            is Resource.Failure -> SettingsUiState.Failure(
                errorMessage = settings.message ?: "Can't load settings",
            )

            is Resource.Success -> SettingsUiState.Content(
                isUpdating = state.isUpdating,
                distanceUnit = settings.value.distanceUnit,
            )
        }
    }
}
