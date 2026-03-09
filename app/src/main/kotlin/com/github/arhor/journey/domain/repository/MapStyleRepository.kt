package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.model.MapResolvedStyle
import com.github.arhor.journey.domain.model.MapStyle

interface MapStyleRepository {

    fun resolve(style: MapStyle): MapResolvedStyle
}
