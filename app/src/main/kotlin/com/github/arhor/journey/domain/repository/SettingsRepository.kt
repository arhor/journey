package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.DistanceUnit
import com.github.arhor.journey.domain.model.MapStyle
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    fun observeSettings(): Flow<AppSettings>

    suspend fun setDistanceUnit(unit: DistanceUnit)

    suspend fun setMapStyle(style: MapStyle)
}
