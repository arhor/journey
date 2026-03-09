package com.github.arhor.journey.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hero")
data class HeroEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val level: Int,
    @ColumnInfo(name = "xp_in_level")
    val xpInLevel: Long,
    val strength: Int,
    val vitality: Int,
    val dexterity: Int,
    val stamina: Int,
    @ColumnInfo(name = "energy_current")
    val energyCurrent: Int,
    @ColumnInfo(name = "energy_max")
    val energyMax: Int,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long,
)

