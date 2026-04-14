package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.ExploredTileEntity
import com.github.arhor.journey.domain.model.MapTile

fun ExploredTileEntity.toDomain(): MapTile =
    MapTile(
        zoom = zoom,
        x = x,
        y = y,
    )

fun MapTile.toEntity(): ExploredTileEntity =
    ExploredTileEntity(
        zoom = zoom,
        x = x,
        y = y,
    )
