package com.github.arhor.journey.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_log")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val type: String,
    val source: String,
    @ColumnInfo(name = "started_at_ms")
    val startedAtMs: Long,
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Long,
    @ColumnInfo(name = "distance_meters")
    val distanceMeters: Int?,
    val steps: Int?,
    val note: String?,
    @ColumnInfo(name = "reward_xp")
    val rewardXp: Long,
)

