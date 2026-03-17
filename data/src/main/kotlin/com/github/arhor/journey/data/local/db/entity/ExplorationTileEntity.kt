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
) : Comparable<ExplorationTileEntity> {

    override fun compareTo(other: ExplorationTileEntity): Int = COMPARATOR.compare(this, other)

    companion object {
        val COMPARATOR = compareBy(
            ExplorationTileEntity::zoom,
            ExplorationTileEntity::y,
            ExplorationTileEntity::x,
        )
    }
}
