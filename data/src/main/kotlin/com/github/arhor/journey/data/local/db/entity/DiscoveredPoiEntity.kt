package com.github.arhor.journey.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import java.time.Instant

@Entity(
    tableName = "discovered_poi",
    primaryKeys = ["poiId"],
    foreignKeys = [
        ForeignKey(
            entity = PoiEntity::class,
            parentColumns = ["id"],
            childColumns = ["poiId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class DiscoveredPoiEntity(
    val poiId: Long = 0,
    val discoveredAt: Instant,
)
