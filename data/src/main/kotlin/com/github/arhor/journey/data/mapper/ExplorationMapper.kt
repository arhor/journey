package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.DiscoveredPoiEntity
import com.github.arhor.journey.data.local.db.entity.ExplorationTileEntity
import com.github.arhor.journey.domain.model.DiscoveredPoi
import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileLight
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

fun ExplorationTileEntity.toDomain(): ExplorationTileLight =
    ExplorationTileLight(
        tile = ExplorationTile(
            zoom = zoom,
            x = x,
            y = y,
        ),
        light = light,
    )

fun ExplorationTileLight.toEntity(): ExplorationTileEntity =
    ExplorationTileEntity(
        zoom = tile.zoom,
        x = tile.x,
        y = tile.y,
        light = light,
    )
