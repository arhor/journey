package com.github.arhor.journey.ui.views.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.github.arhor.journey.core.logging.LoggerFactory
import com.github.arhor.journey.core.logging.NoOpLoggerFactory
import com.github.arhor.journey.data.healthconnect.HealthConnectPermissionGateway
import com.github.arhor.journey.domain.model.ActivityLogEntry
import com.github.arhor.journey.domain.model.ActivitySource
import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.Resource
import com.github.arhor.journey.domain.usecase.ObserveActivityLogUseCase
import com.github.arhor.journey.domain.usecase.ObserveSettingsUseCase
import com.github.arhor.journey.domain.usecase.SetDistanceUnitUseCase
import com.github.arhor.journey.ui.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject

@Immutable
private data class State(
    val isUpdating: Boolean = false,
    val healthConnectConnectionStatus: HealthConnectConnectionStatus = HealthConnectConnectionStatus.DISCONNECTED,
    val healthConnectPermissionStatus: HealthConnectPermissionStatus = HealthConnectPermissionStatus.NOT_REQUESTED,
    val missingHealthConnectPermissions: Set<String> = emptySet(),
    val lastSyncTimestamp: Instant? = null,
    val isSyncInProgress: Boolean = false,
)

@Stable
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val observeSettings: ObserveSettingsUseCase,
    private val observeActivityLog: ObserveActivityLogUseCase,
    private val setDistanceUnit: SetDistanceUnitUseCase,
    private val healthConnectPermissionGateway: HealthConnectPermissionGateway,
    private val clock: Clock,
    loggerFactory: LoggerFactory = NoOpLoggerFactory,
) : MviViewModel<SettingsUiState, SettingsEffect, SettingsIntent>(
    loggerFactory = loggerFactory,
    initialState = SettingsUiState.Loading,
) {
    private val _state = MutableStateFlow(State())

    override fun buildUiState(): Flow<SettingsUiState> =
        combine(_state, observeSettings(), observeActivityLog(), ::intoUiState)
            .distinctUntilChanged()

    override suspend fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SelectDistanceUnit -> handleSelectDistanceUnit(intent)
            SettingsIntent.ConnectHealthConnect,
            SettingsIntent.ManageHealthConnectPermissions,
            -> handleConnectHealthConnect()

            SettingsIntent.ManualSyncHealthData -> handleManualSyncHealthData()
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

    private suspend fun handleManualSyncHealthData() {
        if (_state.value.isSyncInProgress) {
            return
        }

        _state.update { it.copy(isSyncInProgress = true) }
        try {
            val missingPermissions = healthConnectPermissionGateway.getMissingPermissions()
            if (missingPermissions.isNotEmpty()) {
                _state.update {
                    it.copy(
                        isSyncInProgress = false,
                        healthConnectConnectionStatus = HealthConnectConnectionStatus.DISCONNECTED,
                        healthConnectPermissionStatus = HealthConnectPermissionStatus.REQUESTING,
                        missingHealthConnectPermissions = missingPermissions,
                    )
                }
                emitEffect(SettingsEffect.LaunchHealthConnectPermissionRequest(missingPermissions))
                return
            }

            _state.update {
                it.copy(
                    isSyncInProgress = false,
                    lastSyncTimestamp = clock.instant(),
                    healthConnectConnectionStatus = HealthConnectConnectionStatus.CONNECTED,
                    healthConnectPermissionStatus = HealthConnectPermissionStatus.GRANTED,
                    missingHealthConnectPermissions = emptySet(),
                )
            }
            emitEffect(SettingsEffect.Success("Health data sync completed."))
        } catch (e: Throwable) {
            _state.update { it.copy(isSyncInProgress = false) }
            emitEffect(SettingsEffect.Error(message = e.message ?: "Failed to sync health data."))
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

        if (hasAllRequiredPermissions) {
            emitEffect(SettingsEffect.Success("Health Connect permissions granted."))
        } else {
            emitEffect(SettingsEffect.Error("Health Connect permissions are still missing."))
        }
    }

    private fun intoUiState(
        state: State,
        settings: Resource<AppSettings>,
        activityLog: List<ActivityLogEntry>,
    ): SettingsUiState {
        return when (settings) {
            is Resource.Loading -> SettingsUiState.Loading
            is Resource.Failure -> SettingsUiState.Failure(
                errorMessage = settings.message ?: "Can't load settings",
            )

            is Resource.Success -> {
                val importedTodaySummary = importedSummaryForDays(activityLog = activityLog, days = 1)
                val importedWeekSummary = importedSummaryForDays(activityLog = activityLog, days = 7)
                SettingsUiState.Content(
                    isUpdating = state.isUpdating,
                    distanceUnit = settings.value.distanceUnit,
                    healthConnectConnectionStatus = state.healthConnectConnectionStatus,
                    healthConnectPermissionStatus = state.healthConnectPermissionStatus,
                    missingHealthConnectPermissions = state.missingHealthConnectPermissions,
                    lastSyncTimestamp = state.lastSyncTimestamp,
                    isSyncInProgress = state.isSyncInProgress,
                    importedTodaySummary = importedTodaySummary,
                    importedWeekSummary = importedWeekSummary,
                )
            }
        }
    }

    private fun importedSummaryForDays(
        activityLog: List<ActivityLogEntry>,
        days: Long,
    ): ImportedActivitySummary {
        val today = clock.instant().atZone(ZoneOffset.UTC).toLocalDate()
        val thresholdDate = today.minusDays(days - 1)
        val importedEntries = activityLog.filter { entry ->
            entry.recorded.source == ActivitySource.IMPORTED &&
                entry.recorded.startedAt.atZone(ZoneOffset.UTC).toLocalDate() >= thresholdDate
        }

        return ImportedActivitySummary(
            importedActivities = importedEntries.size,
            importedSteps = importedEntries.sumOf { it.recorded.steps?.toLong() ?: 0L },
        )
    }
}
