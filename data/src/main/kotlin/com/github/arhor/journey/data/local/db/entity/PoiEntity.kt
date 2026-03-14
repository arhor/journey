package com.github.arhor.journey.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "poi")
data class PoiEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String?,
    val category: String,
    val lat: Double,
    val lon: Double,
    @ColumnInfo(name = "radius_meters")
    val radiusMeters: Int,
)

