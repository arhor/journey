package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.core.common.State
import com.github.arhor.journey.domain.model.MapStyle
import kotlinx.coroutines.flow.StateFlow

interface MapStylesRepository {

    fun observeMapStyles(): StateFlow<State<List<MapStyle>, MapStylesError>>
}
