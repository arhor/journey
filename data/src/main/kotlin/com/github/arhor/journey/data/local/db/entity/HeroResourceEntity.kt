package com.github.arhor.journey.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import java.time.Instant

@Entity(
    tableName = "hero_resources",
    primaryKeys = ["hero_id", "type_id"],
    foreignKeys = [
        ForeignKey(
            entity = HeroEntity::class,
            parentColumns = ["id"],
            childColumns = ["hero_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class HeroResourceEntity(
    @ColumnInfo(name = "hero_id")
    val heroId: String,
    @ColumnInfo(name = "type_id")
    val typeId: String,
    val amount: Int,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
)
