package com.github.arhor.journey.ui.views.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.github.arhor.journey.core.logging.LoggerFactory
import com.github.arhor.journey.core.logging.NoOpLoggerFactory
import com.github.arhor.journey.data.healthconnect.HealthConnectPermissionGateway
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
    val healthConnectConnectionStatus: HealthConnectConnectionStatus = HealthConnectConnectionStatus.DISCONNECTED,
    val healthConnectPermissionStatus: HealthConnectPermissionStatus = HealthConnectPermissionStatus.NOT_REQUESTED,
    val missingHealthConnectPermissions: Set<String> = emptySet(),
)

@Stable
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val observeSettings: ObserveSettingsUseCase,
    private val setDistanceUnit: SetDistanceUnitUseCase,
    private val healthConnectPermissionGateway: HealthConnectPermissionGateway,
    loggerFactory: LoggerFactory = NoOpLoggerFactory,
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
            SettingsIntent.ConnectHealthConnect -> handleConnectHealthConnect()
            SettingsIntent.HealthConnectPermissionRequestLaunched -> handleHealthConnectPermissionRequestLaunched()
            is SettingsIntent.HandleHealthConnectPermissionResult -> {
                handleHealthConnectPermissionResult(intent)
            }
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
            emitEffect(SettingsEffect.Error(message = e.message ?: "Failed to update distance unit."))
        } finally {
            _state.update { it.copy(isUpdating = false) }
        }
    }

    private suspend fun handleConnectHealthConnect() {
        _state.update {
            it.copy(
                healthConnectConnectionStatus = HealthConnectConnectionStatus.CONNECTING,
            )
        }

        try {
            val missingPermissions = healthConnectPermissionGateway.getMissingPermissions()
            if (missingPermissions.isEmpty()) {
                _state.update {
                    it.copy(
                        healthConnectConnectionStatus = HealthConnectConnectionStatus.CONNECTED,
                        healthConnectPermissionStatus = HealthConnectPermissionStatus.GRANTED,
                        missingHealthConnectPermissions = emptySet(),
                    )
                }
                return
            }

            _state.update {
                it.copy(
                    healthConnectPermissionStatus = HealthConnectPermissionStatus.REQUESTING,
                    missingHealthConnectPermissions = missingPermissions,
                )
            }
            emitEffect(SettingsEffect.LaunchHealthConnectPermissionRequest(missingPermissions))
        } catch (e: Throwable) {
            _state.update {
                it.copy(
                    healthConnectConnectionStatus = HealthConnectConnectionStatus.DISCONNECTED,
                    healthConnectPermissionStatus = HealthConnectPermissionStatus.NOT_REQUESTED,
                )
            }
            emitEffect(SettingsEffect.Error(message = e.message ?: "Failed to connect Health Connect."))
        }
    }

    private fun handleHealthConnectPermissionRequestLaunched() {
        _state.update {
            it.copy(
                healthConnectPermissionStatus = HealthConnectPermissionStatus.REQUESTING,
            )
        }
    }

    private suspend fun handleHealthConnectPermissionResult(
        intent: SettingsIntent.HandleHealthConnectPermissionResult,
    ) {
        val missingPermissions = healthConnectPermissionGateway.requiredPermissions - intent.grantedPermissions
        val hasAllRequiredPermissions = missingPermissions.isEmpty()

        _state.update {
            it.copy(
                healthConnectConnectionStatus = if (hasAllRequiredPermissions) {
                    HealthConnectConnectionStatus.CONNECTED
                } else {
                    HealthConnectConnectionStatus.DISCONNECTED
                },
                healthConnectPermissionStatus = if (hasAllRequiredPermissions) {
                    HealthConnectPermissionStatus.GRANTED
                } else {
                    HealthConnectPermissionStatus.DENIED
                },
                missingHealthConnectPermissions = missingPermissions,
            )
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
                healthConnectConnectionStatus = state.healthConnectConnectionStatus,
                healthConnectPermissionStatus = state.healthConnectPermissionStatus,
                missingHealthConnectPermissions = state.missingHealthConnectPermissions,
            )
        }
    }
}
