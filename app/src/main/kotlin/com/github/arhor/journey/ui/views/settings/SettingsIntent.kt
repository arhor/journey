package com.github.arhor.journey.ui.views.settings

import com.github.arhor.journey.domain.model.DistanceUnit

sealed interface SettingsIntent {

    data class SelectDistanceUnit(val unit: DistanceUnit) : SettingsIntent

    data object ConnectHealthConnect : SettingsIntent

    data object HealthConnectPermissionRequestLaunched : SettingsIntent

    data class HandleHealthConnectPermissionResult(val grantedPermissions: Set<String>) : SettingsIntent
}
