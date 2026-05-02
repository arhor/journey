package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.error.AppSettingsError
import kotlinx.coroutines.flow.Flow

interface AppSettingsRepository {

    fun observeAvailableMapStyles(): Flow<Output<List<MapStyle>, AppSettingsError>>

    fun observeSelectedMapStyle(): Flow<Output<MapStyle, AppSettingsError>>

    suspend fun setSelectedMapStyle(styleId: String): Output<Unit, AppSettingsError>
}
