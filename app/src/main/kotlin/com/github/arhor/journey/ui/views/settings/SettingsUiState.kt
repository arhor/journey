package com.github.arhor.journey.ui.views.settings

import androidx.compose.runtime.Immutable
import com.github.arhor.journey.domain.model.DistanceUnit
import com.github.arhor.journey.domain.model.HealthConnectAvailability
import java.time.Instant

sealed interface SettingsUiState {

    @Immutable
    data object Loading : SettingsUiState

    @Immutable
    data class Failure(val errorMessage: String) : SettingsUiState

    @Immutable
    data class Content(
        val isUpdating: Boolean,
        val distanceUnit: DistanceUnit,
        val healthConnectAvailability: HealthConnectAvailability,
        val healthConnectConnectionStatus: HealthConnectConnectionStatus,
        val healthConnectPermissionStatus: HealthConnectPermissionStatus,
        val missingHealthConnectPermissions: Set<String>,
        val lastSyncTimestamp: Instant?,
        val isSyncInProgress: Boolean,
        val importedTodaySummary: ImportedActivitySummary,
        val importedWeekSummary: ImportedActivitySummary,
    ) : SettingsUiState
}

@Immutable
data class ImportedActivitySummary(
    val importedActivities: Int = 0,
    val importedSteps: Long = 0,
)

enum class HealthConnectConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

enum class HealthConnectPermissionStatus {
    NOT_REQUESTED,
    REQUESTING,
    GRANTED,
    DENIED,
}
