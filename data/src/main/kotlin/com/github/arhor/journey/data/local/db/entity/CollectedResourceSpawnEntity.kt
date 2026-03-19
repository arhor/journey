package com.github.arhor.journey.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.time.Instant

@Entity(
    tableName = "collected_resource_spawns",
    primaryKeys = ["heroId", "spawnId"],
    foreignKeys = [
        ForeignKey(
            entity = HeroEntity::class,
            parentColumns = ["id"],
            childColumns = ["heroId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["heroId", "collectedAt"]),
    ],
)
data class CollectedResourceSpawnEntity(
    val heroId: String,
    val typeId: String,
    val spawnId: String,
    val collectedAt: Instant,
)
