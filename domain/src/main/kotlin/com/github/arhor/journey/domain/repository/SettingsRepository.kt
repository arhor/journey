package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.DistanceUnit
import com.github.arhor.journey.domain.model.error.AppSettingsError
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    fun observeSettings(): Flow<Output<AppSettings, AppSettingsError>>

    suspend fun setDistanceUnit(unit: DistanceUnit)

    suspend fun setSelectedMapStyleId(styleId: String)
}
