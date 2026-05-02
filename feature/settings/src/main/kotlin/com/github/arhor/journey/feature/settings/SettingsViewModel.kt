package com.github.arhor.journey.feature.settings

import androidx.compose.runtime.Stable
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.common.resolveMessage
import com.github.arhor.journey.core.ui.MviViewModel
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.error.AppSettingsError
import com.github.arhor.journey.domain.usecase.ObserveAvailableMapStylesUseCase
import com.github.arhor.journey.domain.usecase.ObserveSelectedMapStyleUseCase
import com.github.arhor.journey.domain.usecase.SetSelectedMapStyleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@Stable
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val observeAvailableMapStyles: ObserveAvailableMapStylesUseCase,
    private val observeSelectedMapStyle: ObserveSelectedMapStyleUseCase,
    private val setSelectedMapStyle: SetSelectedMapStyleUseCase,
) : MviViewModel<SettingsUiState, SettingsEffect, SettingsIntent>(
    initialState = SettingsUiState.Loading,
) {
    override fun buildUiState(): Flow<SettingsUiState> =
        combine(
            observeAvailableMapStyles(),
            observeSelectedMapStyle(),
        ) { availableStyles, selectedStyle ->
            toUiState(
                availableStylesOutput = availableStyles,
                selectedStyleOutput = selectedStyle,
            )
        }
            .catch { error ->
                emit(
                    SettingsUiState.Failure(
                        error.message ?: SETTINGS_LOADING_FAILED_MESSAGE,
                    ),
                )
            }

    override suspend fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.MapStyleSelected -> onMapStyleSelected(intent.styleId)
        }
    }

    private fun toUiState(
        availableStylesOutput: Output<List<MapStyle>, AppSettingsError>,
        selectedStyleOutput: Output<MapStyle, AppSettingsError>,
    ): SettingsUiState = when (availableStylesOutput) {
        is Output.Failure -> SettingsUiState.Failure(
            availableStylesOutput.error.resolveMessage(SETTINGS_LOADING_FAILED_MESSAGE),
        )

        is Output.Success -> when (selectedStyleOutput) {
            is Output.Failure -> SettingsUiState.Failure(
                selectedStyleOutput.error.resolveMessage(SETTINGS_LOADING_FAILED_MESSAGE),
            )

            is Output.Success -> SettingsUiState.Content(
                mapStyles = availableStylesOutput.value,
                selectedMapStyleId = selectedStyleOutput.value.id,
            )
        }
    }

    private suspend fun onMapStyleSelected(styleId: String) {
        when (val result = setSelectedMapStyle(styleId)) {
            is Output.Success -> Unit
            is Output.Failure -> emitEffect(
                SettingsEffect.Error(
                    result.error.resolveMessage(SETTINGS_SAVE_FAILED_MESSAGE),
                ),
            )
        }
    }

    private companion object {
        const val SETTINGS_LOADING_FAILED_MESSAGE = "Failed to load settings."
        const val SETTINGS_SAVE_FAILED_MESSAGE = "Failed to save settings."
    }
}
