package com.github.arhor.journey.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.time.Instant

@Entity(
    tableName = "collected_resource_spawns",
    primaryKeys = ["hero_id", "spawn_id"],
    foreignKeys = [
        ForeignKey(
            entity = HeroEntity::class,
            parentColumns = ["id"],
            childColumns = ["hero_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["hero_id", "collected_at"]),
    ],
)
data class CollectedResourceSpawnEntity(
    @ColumnInfo(name = "hero_id")
    val heroId: String,
    @ColumnInfo(name = "type_id")
    val typeId: String,
    @ColumnInfo(name = "spawn_id")
    val spawnId: String,
    @ColumnInfo(name = "collected_at")
    val collectedAt: Instant,
)
