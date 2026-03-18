package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.DiscoveredPoiEntity
import com.github.arhor.journey.data.local.db.entity.ExplorationTileEntity
import com.github.arhor.journey.domain.model.DiscoveredPoi
import com.github.arhor.journey.domain.model.ExplorationTile
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

fun ExplorationTileEntity.toDomain(): ExplorationTile =
    ExplorationTile(
        zoom = zoom,
        x = x,
        y = y,
    )

fun ExplorationTile.toEntity(): ExplorationTileEntity =
    ExplorationTileEntity(
        zoom = zoom,
        x = x,
        y = y,
    )
