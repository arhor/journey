package com.github.arhor.journey.data.mapobject

import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.ResourceSpawn
import java.time.Instant

interface MapObjectAreaSource {

    suspend fun fetchArea(
        bounds: GeoBounds,
        asOf: Instant,
    ): MapObjectAreaResponse

    suspend fun fetchActiveResourceSpawn(
        spawnId: String,
        asOf: Instant,
    ): ResourceSpawn?
}
