package com.github.arhor.journey.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "poi")
data class PoiEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String?,
    val category: String,
    val lat: Double,
    val lon: Double,
    val radiusMeters: Int,
)
