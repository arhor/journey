package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.model.MapStyle

interface MapStylesRepository {

    fun findAll(): List<MapStyle>
}
