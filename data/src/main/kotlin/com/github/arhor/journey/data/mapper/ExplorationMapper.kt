package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.DiscoveredPoiEntity
import com.github.arhor.journey.data.local.db.entity.ExploredTileEntity
import com.github.arhor.journey.domain.model.DiscoveredPoi
import com.github.arhor.journey.domain.model.ExplorationTile

fun DiscoveredPoiEntity.toDomain(): DiscoveredPoi =
    DiscoveredPoi(
        poiId = poiId,
        discoveredAt = discoveredAt,
    )

fun DiscoveredPoi.toEntity(): DiscoveredPoiEntity =
    DiscoveredPoiEntity(
        poiId = poiId,
        discoveredAt = discoveredAt,
    )

fun ExploredTileEntity.toDomain(): ExplorationTile =
    ExplorationTile(
        zoom = zoom,
        x = x,
        y = y,
    )

fun ExplorationTile.toEntity(): ExploredTileEntity =
    ExploredTileEntity(
        zoom = zoom,
        x = x,
        y = y,
    )
