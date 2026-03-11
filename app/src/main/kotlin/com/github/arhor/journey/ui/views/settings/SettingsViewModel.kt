package com.github.arhor.journey.ui.views.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.github.arhor.journey.domain.activity.model.ActivityLogEntry
import com.github.arhor.journey.domain.activity.model.ActivitySource
import com.github.arhor.journey.domain.settings.model.AppSettings
import com.github.arhor.journey.domain.map.model.MapStyle
import com.github.arhor.journey.domain.model.HealthConnectAvailability
import com.github.arhor.journey.domain.model.HealthDataSyncFailure
import com.github.arhor.journey.domain.model.HealthDataSyncMode
import com.github.arhor.journey.domain.model.HealthDataTimeRange
import com.github.arhor.journey.domain.model.HealthDataType
import com.github.arhor.journey.domain.repository.HealthConnectAvailabilityRepository
import com.github.arhor.journey.domain.repository.HealthPermissionRepository
import com.github.arhor.journey.domain.repository.HealthSyncCheckpointRepository
import com.github.arhor.journey.domain.usecase.ObserveActivityLogUseCase
import com.github.arhor.journey.domain.usecase.ObserveAvailableMapStylesUseCase
import com.github.arhor.journey.domain.usecase.ObserveSelectedMapStyleUseCase
import com.github.arhor.journey.domain.usecase.ObserveSettingsUseCase
import com.github.arhor.journey.domain.usecase.SetDistanceUnitUseCase
import com.github.arhor.journey.domain.usecase.SetMapStyleUseCase
import com.github.arhor.journey.domain.usecase.SyncHealthDataUseCase
import com.github.arhor.journey.domain.model.SyncHealthDataUseCaseResult
import com.github.arhor.journey.ui.MviViewModel
import com.github.arhor.journey.ui.views.settings.model.HealthConnectConnectionStatus
import com.github.arhor.journey.ui.views.settings.model.HealthConnectPermissionStatus
import com.github.arhor.journey.ui.views.settings.model.ImportedActivitySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject

@Immutable
private data class State(
    val isUpdating: Boolean = false,
    val availability: HealthConnectAvailability = HealthConnectAvailability.AVAILABLE,
    val connectionStatus: HealthConnectConnectionStatus = HealthConnectConnectionStatus.DISCONNECTED,
    val permissionStatus: HealthConnectPermissionStatus = HealthConnectPermissionStatus.NOT_REQUESTED,
    val permissions: Set<String> = emptySet(),
    val lastSyncTimestamp: Instant? = null,
    val isSyncInProgress: Boolean = false,
    val consecutivePermissionRequestFailures: Int = 0,
    val pendingHealthConnectAction: PendingHealthConnectAction? = null,
)

private enum class PendingHealthConnectAction {
    CONNECT,
    SYNC,
}

@Stable
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val observeSettings: ObserveSettingsUseCase,
    private val observeAvailableMapStyles: ObserveAvailableMapStylesUseCase,
    private val observeSelectedMapStyle: ObserveSelectedMapStyleUseCase,
    private val observeActivityLog: ObserveActivityLogUseCase,
    private val setDistanceUnit: SetDistanceUnitUseCase,
    private val setMapStyle: SetMapStyleUseCase,
    private val healthPermissionRepository: HealthPermissionRepository,
    private val healthConnectAvailabilityRepository: HealthConnectAvailabilityRepository,
    private val healthSyncCheckpointRepository: HealthSyncCheckpointRepository,
    private val syncHealthData: SyncHealthDataUseCase,
    private val clock: Clock,
) : MviViewModel<SettingsUiState, SettingsEffect, SettingsIntent>(
    initialState = SettingsUiState.Loading,
) {
    private val _state = MutableStateFlow(
        State(
            availability = runCatching {
                healthConnectAvailabilityRepository.checkAvailability()
            }.getOrDefault(HealthConnectAvailability.AVAILABLE),
        ),
    )

    init {
        viewModelScope.launch {
            onRefreshHealthConnectStatus()
        }
    }

    override fun buildUiState(): Flow<SettingsUiState> =
        combine(
            _state,
            observeSettings(),
            observeAvailableMapStyles(),
            observeSelectedMapStyle(),
            observeActivityLog(),
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
            is SettingsIntent.ConnectHealthConnect -> onConnectHealthConnect()
            is SettingsIntent.ManageHealthConnectPermissions -> onManageHealthConnectPermissions()
            is SettingsIntent.ManualSyncHealthData -> onManualSyncHealthData()
            is SettingsIntent.RefreshHealthConnectStatus -> onRefreshHealthConnectStatus()
            is SettingsIntent.HealthConnectPermissionRequestLaunched -> onHealthConnectPermissionRequestLaunched()
            is SettingsIntent.HandleHealthConnectPermissionResult -> onHandleHealthConnectPermissionResult()
        }
    }

    /* ------------------------------------------ Internal implementation ------------------------------------------- */

    private fun intoUiState(
        state: State,
        settings: AppSettings,
        availableMapStyles: List<MapStyle>,
        selectedMapStyle: MapStyle,
        activityLog: List<ActivityLogEntry>,
    ): SettingsUiState {
        val importedTodaySummary = importedSummaryForDays(activityLog = activityLog, days = 1)
        val importedWeekSummary = importedSummaryForDays(activityLog = activityLog, days = 7)

        return SettingsUiState.Content(
            isUpdating = state.isUpdating,
            distanceUnit = settings.distanceUnit,
            selectedMapStyleId = selectedMapStyle.id,
            availableMapStyles = availableMapStyles,
            healthConnectAvailability = state.availability,
            healthConnectConnectionStatus = state.connectionStatus,
            healthConnectPermissionStatus = state.permissionStatus,
            missingHealthConnectPermissions = state.permissions,
            lastSyncTimestamp = state.lastSyncTimestamp,
            isSyncInProgress = state.isSyncInProgress,
            importedTodaySummary = importedTodaySummary,
            importedWeekSummary = importedWeekSummary,
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

    private suspend fun onConnectHealthConnect() {
        _state.update {
            it.copy(
                connectionStatus = HealthConnectConnectionStatus.CONNECTING,
                pendingHealthConnectAction = null,
            )
        }

        try {
            val missingPermissions = readMissingPermissions()
            if (missingPermissions == null) {
                emitHealthConnectAvailabilityError(openManagement = true)
                return
            }

            if (missingPermissions.isEmpty()) {
                _state.update {
                    it.copy(
                        connectionStatus = HealthConnectConnectionStatus.CONNECTED,
                        permissionStatus = HealthConnectPermissionStatus.GRANTED,
                        permissions = emptySet(),
                        consecutivePermissionRequestFailures = 0,
                    )
                }
                return
            }

            launchHealthConnectPermissionRequest(
                missingPermissions = missingPermissions,
                pendingAction = PendingHealthConnectAction.CONNECT,
            )
        } catch (e: Throwable) {
            _state.update {
                it.copy(
                    connectionStatus = HealthConnectConnectionStatus.DISCONNECTED,
                    permissionStatus = HealthConnectPermissionStatus.NOT_REQUESTED,
                    pendingHealthConnectAction = null,
                )
            }
            emitEffect(SettingsEffect.Error(message = e.message ?: "Failed to connect Health Connect."))
        }
    }

    private suspend fun onManageHealthConnectPermissions() {
        val availability = healthConnectAvailabilityRepository.checkAvailability()
        _state.update { it.copy(availability = availability) }

        if (availability == HealthConnectAvailability.NOT_SUPPORTED) {
            updateUnavailableHealthConnectState(availability = availability)
            emitHealthConnectAvailabilityError(openManagement = false)
            return
        }

        emitEffect(SettingsEffect.OpenHealthConnectManagement)
    }

    private suspend fun onManualSyncHealthData() {
        if (_state.value.isSyncInProgress) {
            return
        }

        _state.update {
            it.copy(
                isSyncInProgress = true,
                pendingHealthConnectAction = null,
            )
        }

        try {
            val missingPermissions = readMissingPermissions()
            if (missingPermissions == null) {
                emitHealthConnectAvailabilityError(openManagement = true)
                return
            }

            if (missingPermissions.isNotEmpty()) {
                launchHealthConnectPermissionRequest(
                    missingPermissions = missingPermissions,
                    pendingAction = PendingHealthConnectAction.SYNC,
                )
                return
            }

            performManualSync()
        } catch (e: Throwable) {
            _state.update {
                it.copy(
                    isSyncInProgress = false,
                    pendingHealthConnectAction = null,
                )
            }
            emitEffect(SettingsEffect.Error(message = e.message ?: "Failed to sync health data."))
        }
    }

    private fun onHealthConnectPermissionRequestLaunched() {
        _state.update {
            it.copy(
                permissionStatus = HealthConnectPermissionStatus.REQUESTING,
            )
        }
    }

    private suspend fun onHandleHealthConnectPermissionResult() {
        val pendingAction = _state.value.pendingHealthConnectAction

        try {
            val missingPermissions = readMissingPermissions()
            if (missingPermissions == null) {
                emitHealthConnectAvailabilityError(openManagement = pendingAction == PendingHealthConnectAction.SYNC)
                return
            }

            val hasAllRequiredPermissions = missingPermissions.isEmpty()
            val nextFailureCount = if (hasAllRequiredPermissions) {
                0
            } else {
                _state.value.consecutivePermissionRequestFailures + 1
            }

            _state.update {
                it.copy(
                    connectionStatus = if (hasAllRequiredPermissions) {
                        HealthConnectConnectionStatus.CONNECTED
                    } else {
                        HealthConnectConnectionStatus.DISCONNECTED
                    },
                    permissionStatus = if (hasAllRequiredPermissions) {
                        HealthConnectPermissionStatus.GRANTED
                    } else {
                        HealthConnectPermissionStatus.DENIED
                    },
                    permissions = missingPermissions,
                    consecutivePermissionRequestFailures = nextFailureCount,
                    pendingHealthConnectAction = null,
                    isSyncInProgress = false,
                )
            }

            if (hasAllRequiredPermissions) {
                if (pendingAction == PendingHealthConnectAction.SYNC) {
                    performManualSync()
                } else {
                    emitEffect(SettingsEffect.Success("Health Connect permissions granted."))
                }
            } else {
                val message = if (nextFailureCount >= 2) {
                    "Health Connect permissions are still missing. Use Manage Permissions to continue."
                } else {
                    "Health Connect permissions are still missing."
                }
                emitEffect(SettingsEffect.Error(message))
            }
        } catch (e: Throwable) {
            _state.update {
                it.copy(
                    connectionStatus = HealthConnectConnectionStatus.DISCONNECTED,
                    permissionStatus = HealthConnectPermissionStatus.NOT_REQUESTED,
                    pendingHealthConnectAction = null,
                    isSyncInProgress = false,
                )
            }
            emitEffect(SettingsEffect.Error(message = e.message ?: "Failed to update Health Connect permissions."))
        }
    }

    private suspend fun onRefreshHealthConnectStatus() {
        val lastSyncTimestamp = runCatching {
            healthSyncCheckpointRepository.getLastSuccessfulSyncAt()
        }.getOrNull()
        _state.update { it.copy(lastSyncTimestamp = lastSyncTimestamp) }

        val availability = runCatching {
            healthConnectAvailabilityRepository.checkAvailability()
        }.getOrElse {
            _state.update {
                it.copy(
                    connectionStatus = HealthConnectConnectionStatus.DISCONNECTED,
                    permissionStatus = HealthConnectPermissionStatus.NOT_REQUESTED,
                    permissions = emptySet(),
                    pendingHealthConnectAction = null,
                    isSyncInProgress = false,
                )
            }
            return
        }
        if (availability != HealthConnectAvailability.AVAILABLE) {
            updateUnavailableHealthConnectState(availability = availability)
            return
        }

        val missingPermissions = runCatching {
            healthPermissionRepository.getMissingPermissions()
        }.getOrElse {
            _state.update {
                it.copy(
                    availability = availability,
                    connectionStatus = HealthConnectConnectionStatus.DISCONNECTED,
                    permissionStatus = HealthConnectPermissionStatus.NOT_REQUESTED,
                    permissions = emptySet(),
                    pendingHealthConnectAction = null,
                    isSyncInProgress = false,
                )
            }
            return
        }

        _state.update {
            it.copy(
                availability = availability,
                connectionStatus = if (missingPermissions.isEmpty()) {
                    HealthConnectConnectionStatus.CONNECTED
                } else {
                    HealthConnectConnectionStatus.DISCONNECTED
                },
                permissionStatus = if (missingPermissions.isEmpty()) {
                    HealthConnectPermissionStatus.GRANTED
                } else if (it.permissionStatus == HealthConnectPermissionStatus.DENIED) {
                    HealthConnectPermissionStatus.DENIED
                } else {
                    HealthConnectPermissionStatus.NOT_REQUESTED
                },
                permissions = missingPermissions,
                consecutivePermissionRequestFailures = if (missingPermissions.isEmpty()) 0 else {
                    it.consecutivePermissionRequestFailures
                },
                pendingHealthConnectAction = null,
                isSyncInProgress = false,
            )
        }
    }

    private suspend fun readMissingPermissions(): Set<String>? {
        val availability = healthConnectAvailabilityRepository.checkAvailability()
        if (availability != HealthConnectAvailability.AVAILABLE) {
            updateUnavailableHealthConnectState(availability = availability)
            return null
        }

        _state.update { it.copy(availability = availability) }
        return healthPermissionRepository.getMissingPermissions()
    }

    private fun updateUnavailableHealthConnectState(availability: HealthConnectAvailability) {
        _state.update {
            it.copy(
                availability = availability,
                connectionStatus = HealthConnectConnectionStatus.DISCONNECTED,
                permissionStatus = HealthConnectPermissionStatus.NOT_REQUESTED,
                permissions = emptySet(),
                pendingHealthConnectAction = null,
                isSyncInProgress = false,
                consecutivePermissionRequestFailures = 0,
            )
        }
    }

    private suspend fun launchHealthConnectPermissionRequest(
        missingPermissions: Set<String>,
        pendingAction: PendingHealthConnectAction,
    ) {
        _state.update {
            it.copy(
                isSyncInProgress = false,
                connectionStatus = if (pendingAction == PendingHealthConnectAction.CONNECT) {
                    HealthConnectConnectionStatus.CONNECTING
                } else {
                    HealthConnectConnectionStatus.DISCONNECTED
                },
                permissionStatus = HealthConnectPermissionStatus.REQUESTING,
                permissions = missingPermissions,
                pendingHealthConnectAction = pendingAction,
            )
        }
        emitEffect(SettingsEffect.LaunchHealthConnectPermissionRequest(missingPermissions))
    }

    private suspend fun performManualSync() {
        _state.update {
            it.copy(
                isSyncInProgress = true,
                pendingHealthConnectAction = null,
            )
        }

        val syncFinishedAt = clock.instant()
        val timeRange = resolveManualSyncTimeRange(end = syncFinishedAt)
        when (
            val result = syncHealthData(
                timeRange = timeRange,
                selectedDataTypes = setOf(HealthDataType.SESSIONS),
                syncMode = HealthDataSyncMode.MANUAL,
            )
        ) {
            is SyncHealthDataUseCaseResult.Success -> {
                healthSyncCheckpointRepository.setLastSuccessfulSyncAt(syncFinishedAt)
                _state.update {
                    it.copy(
                        isSyncInProgress = false,
                        lastSyncTimestamp = syncFinishedAt,
                        connectionStatus = HealthConnectConnectionStatus.CONNECTED,
                        permissionStatus = HealthConnectPermissionStatus.GRANTED,
                        permissions = emptySet(),
                        consecutivePermissionRequestFailures = 0,
                    )
                }
                emitEffect(SettingsEffect.Success("Health data sync completed."))
            }

            is SyncHealthDataUseCaseResult.Failure -> handleManualSyncFailure(result.reason)
        }
    }

    private suspend fun handleManualSyncFailure(reason: HealthDataSyncFailure) {
        when (reason) {
            HealthDataSyncFailure.PermissionMissing -> {
                val missingPermissions = runCatching {
                    readMissingPermissions()
                }.getOrNull().orEmpty()

                if (missingPermissions.isNotEmpty()) {
                    launchHealthConnectPermissionRequest(
                        missingPermissions = missingPermissions,
                        pendingAction = PendingHealthConnectAction.SYNC,
                    )
                } else {
                    _state.update {
                        it.copy(
                            isSyncInProgress = false,
                            pendingHealthConnectAction = null,
                        )
                    }
                    emitEffect(SettingsEffect.Error("Health Connect permissions are still missing."))
                }
            }

            HealthDataSyncFailure.UnavailableProvider -> {
                val availability = healthConnectAvailabilityRepository.checkAvailability()
                updateUnavailableHealthConnectState(availability = availability)
                emitHealthConnectAvailabilityError(openManagement = true)
            }

            HealthDataSyncFailure.EmptyData -> {
                _state.update {
                    it.copy(
                        isSyncInProgress = false,
                        pendingHealthConnectAction = null,
                    )
                }
                emitEffect(SettingsEffect.Success("No health data available to sync."))
            }

            is HealthDataSyncFailure.TransientError -> {
                _state.update {
                    it.copy(
                        isSyncInProgress = false,
                        pendingHealthConnectAction = null,
                    )
                }
                emitEffect(
                    SettingsEffect.Error(
                        message = reason.message ?: "Failed to sync health data.",
                    ),
                )
            }
        }
    }

    private suspend fun resolveManualSyncTimeRange(end: Instant): HealthDataTimeRange {
        val lastSuccessfulSyncAt = healthSyncCheckpointRepository.getLastSuccessfulSyncAt()
        val start = maxOf(
            end.minus(MAX_INCREMENTAL_LOOKBACK),
            lastSuccessfulSyncAt ?: end.minus(DEFAULT_INITIAL_LOOKBACK),
        )

        return HealthDataTimeRange(
            startTime = start,
            endTime = end,
        )
    }

    private fun emitHealthConnectAvailabilityError(openManagement: Boolean) {
        val availability = _state.value.availability

        if (openManagement && availability == HealthConnectAvailability.NEEDS_UPDATE_OR_INSTALL) {
            emitEffect(SettingsEffect.OpenHealthConnectManagement)
        }

        val message = when (availability) {
            HealthConnectAvailability.AVAILABLE -> "Health Connect is available."
            HealthConnectAvailability.NEEDS_UPDATE_OR_INSTALL -> {
                "Install or update Health Connect to continue."
            }

            HealthConnectAvailability.NOT_SUPPORTED -> {
                "Health Connect is not supported on this device."
            }
        }
        emitEffect(SettingsEffect.Error(message))
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

    private companion object {
        const val SETTINGS_LOADING_FAILED_MESSAGE = "Can't load settings."
        val DEFAULT_INITIAL_LOOKBACK: Duration = Duration.ofDays(3)
        val MAX_INCREMENTAL_LOOKBACK: Duration = Duration.ofDays(14)
    }
}
