package com.github.arhor.journey.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import java.time.Instant

@Entity(
    tableName = "discovered_poi",
    primaryKeys = ["poi_id"],
    foreignKeys = [
        ForeignKey(
            entity = PoiEntity::class,
            parentColumns = ["id"],
            childColumns = ["poi_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class DiscoveredPoiEntity(
    @ColumnInfo(name = "poi_id")
    val poiId: Long = 0,
    @ColumnInfo(name = "discovered_at")
    val discoveredAt: Instant,
)
