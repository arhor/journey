package com.github.arhor.journey.ui.views.settings

import com.github.arhor.journey.domain.model.DistanceUnit
import com.github.arhor.journey.domain.model.MapStyle

sealed interface SettingsIntent {

    data class SelectDistanceUnit(val unit: DistanceUnit) : SettingsIntent

    data class SelectMapStyle(val style: MapStyle) : SettingsIntent

    data object ConnectHealthConnect : SettingsIntent

    data object ManageHealthConnectPermissions : SettingsIntent

    data object ManualSyncHealthData : SettingsIntent

    data object RefreshHealthConnectStatus : SettingsIntent

    data object HealthConnectPermissionRequestLaunched : SettingsIntent

    data object HandleHealthConnectPermissionResult : SettingsIntent
}
