package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.ResolvedMapStyle
import kotlinx.coroutines.flow.Flow

interface MapStylesRepository {

    fun observeAvailableStyles(): Flow<List<MapStyle>>

    fun observeSelectedStyle(): Flow<ResolvedMapStyle>
}
