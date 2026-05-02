package com.github.arhor.journey.data.local.db.entity

import androidx.room.Entity

@Entity(
    tableName = "explored_tiles",
    primaryKeys = ["zoom", "x", "y"],
)
data class ExploredTileEntity(
    val zoom: Int,
    val x: Int,
    val y: Int,
)  {

    companion object {
        val COMPARATOR = compareBy(
            ExploredTileEntity::zoom,
            ExploredTileEntity::y,
            ExploredTileEntity::x,
        )
    }
}
