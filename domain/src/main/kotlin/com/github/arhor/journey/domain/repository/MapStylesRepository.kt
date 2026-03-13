package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.MapStyle
import kotlinx.coroutines.flow.StateFlow

interface MapStylesRepository {

    fun observeMapStyles(): StateFlow<Output<List<MapStyle>, MapStylesError>>
}
