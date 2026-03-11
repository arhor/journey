package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.DiscoveredPoiEntity
import com.github.arhor.journey.domain.exploration.model.DiscoveredPoi
import java.time.Instant

fun DiscoveredPoiEntity.toDomain(): DiscoveredPoi =
    DiscoveredPoi(
        poiId = poiId,
        discoveredAt = Instant.ofEpochMilli(discoveredAtMs),
    )

fun DiscoveredPoi.toEntity(): DiscoveredPoiEntity =
    DiscoveredPoiEntity(
        poiId = poiId,
        discoveredAtMs = discoveredAt.toEpochMilli(),
    )

