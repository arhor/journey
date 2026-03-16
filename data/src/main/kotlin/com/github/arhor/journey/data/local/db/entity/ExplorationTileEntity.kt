package com.github.arhor.journey.data.local.db.entity

import androidx.room.Entity

@Entity(
    tableName = "exploration_tile",
    primaryKeys = ["zoom", "x", "y"],
)
data class ExplorationTileEntity(
    val zoom: Int,
    val x: Int,
    val y: Int,
)
