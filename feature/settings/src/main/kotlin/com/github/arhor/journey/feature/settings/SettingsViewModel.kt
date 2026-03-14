package com.github.arhor.journey.feature.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.common.combine
import com.github.arhor.journey.core.common.fold
import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.error.AppSettingsError
import com.github.arhor.journey.domain.model.error.MapStylesError
import com.github.arhor.journey.domain.usecase.ObserveMapStylesUseCase
import com.github.arhor.journey.domain.usecase.ObserveSettingsUseCase
import com.github.arhor.journey.domain.usecase.SetDistanceUnitUseCase
import com.github.arhor.journey.domain.usecase.SetMapStyleUseCase
import com.github.arhor.journey.core.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
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
    private val observeMapStyles: ObserveMapStylesUseCase,
    private val setDistanceUnit: SetDistanceUnitUseCase,
    private val setMapStyle: SetMapStyleUseCase,
) : MviViewModel<SettingsUiState, SettingsEffect, SettingsIntent>(
    initialState = SettingsUiState.Loading,
) {
    private val _state = MutableStateFlow(State())

    override fun buildUiState(): Flow<SettingsUiState> =
        combine(
            _state,
            observeSettings(),
            observeMapStyles(),
            ::intoUiState
        ).catch {
            emit(
                SettingsUiState.Failure(
                    errorMessage = it.message ?: SETTINGS_LOADING_FAILED_MESSAGE
                )
            )
        }.distinctUntilChanged()

    override suspend fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SelectDistanceUnit -> onSelectDistanceUnit(intent)
            is SettingsIntent.SelectMapStyle -> onSelectMapStyle(intent)
        }
    }

    /* ------------------------------------------ Internal implementation ------------------------------------------- */

    private fun intoUiState(
        state: State,
        settingsOutput: Output<AppSettings, AppSettingsError>,
        mapStylesOutput: Output<List<MapStyle>, MapStylesError>,
    ): SettingsUiState {
        return combine(settingsOutput, mapStylesOutput).fold(
            onSuccess = { (settings, mapStyles) ->
                SettingsUiState.Content(
                    isUpdating = state.isUpdating,
                    distanceUnit = settings.distanceUnit,
                    selectedMapStyleId = settings.selectedMapStyleId,
                    availableMapStyles = mapStyles,
                )
            },
            onFailure = {
                SettingsUiState.Failure(
                    errorMessage = it.message
                        ?: it.cause?.message
                        ?: SETTINGS_LOADING_FAILED_MESSAGE,
                )
            },
        )
    }

    private suspend fun onSelectDistanceUnit(intent: SettingsIntent.SelectDistanceUnit) {
        if (_state.value.isUpdating) {
            return
        }

        _state.update { it.copy(isUpdating = true) }
        try {
            setDistanceUnit(intent.unit)
        } catch (e: Throwable) {
            emitEffect(SettingsEffect.Error(message = e.message ?: "Failed to update distance unit."))
        } finally {
            _state.update { it.copy(isUpdating = false) }
        }
    }

    private suspend fun onSelectMapStyle(intent: SettingsIntent.SelectMapStyle) {
        if (_state.value.isUpdating) {
            return
        }

        _state.update { it.copy(isUpdating = true) }
        try {
            setMapStyle(intent.styleId)
        } catch (e: Throwable) {
            emitEffect(SettingsEffect.Error(message = e.message ?: "Failed to update map style."))
        } finally {
            _state.update { it.copy(isUpdating = false) }
        }
    }

    private companion object {
        const val SETTINGS_LOADING_FAILED_MESSAGE = "Can't load settings."
    }
}
