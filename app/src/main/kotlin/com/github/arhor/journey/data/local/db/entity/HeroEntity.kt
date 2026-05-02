package com.github.arhor.journey.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "hero")
data class HeroEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val level: Int,
    val xpInLevel: Long,
    val energyNow: Int,
    val energyMax: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)
