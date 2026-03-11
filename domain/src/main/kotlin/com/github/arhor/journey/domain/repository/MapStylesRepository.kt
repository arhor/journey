package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.map.model.ResolvedMapStyle
import com.github.arhor.journey.domain.map.model.MapStyle
import kotlinx.coroutines.flow.Flow

interface MapStylesRepository {

    fun observeAvailableStyles(): Flow<List<MapStyle>>

    fun observeSelectedStyle(): Flow<ResolvedMapStyle>
}
