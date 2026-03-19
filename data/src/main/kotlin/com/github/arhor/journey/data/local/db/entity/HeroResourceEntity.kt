package com.github.arhor.journey.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import java.time.Instant

@Entity(
    tableName = "hero_resources",
    primaryKeys = ["heroId", "typeId"],
    foreignKeys = [
        ForeignKey(
            entity = HeroEntity::class,
            parentColumns = ["id"],
            childColumns = ["heroId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class HeroResourceEntity(
    val heroId: String,
    val typeId: String,
    val amount: Int,
    val updatedAt: Instant,
)
