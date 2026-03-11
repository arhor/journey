package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.map.model.MapResolvedStyle
import com.github.arhor.journey.domain.map.model.MapStyle
import kotlinx.coroutines.flow.Flow

interface MapStyleRepository {

    fun observeAvailableStyles(): Flow<List<MapStyle>>

    fun observeSelectedStyle(): Flow<MapStyle>

    fun observeSelectedResolvedStyle(): Flow<MapResolvedStyle>

    suspend fun selectStyle(styleId: String)

    suspend fun refreshRemoteStyles(): Result<Unit>
}
