package com.github.arhor.journey.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "discovered_poi",
    primaryKeys = ["poi_id"],
    foreignKeys = [
        ForeignKey(
            entity = PoiEntity::class,
            parentColumns = ["id"],
            childColumns = ["poi_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DiscoveredPoiEntity(
    @ColumnInfo(name = "poi_id")
    val poiId: String,
    @ColumnInfo(name = "discovered_at_ms")
    val discoveredAtMs: Long,
)

