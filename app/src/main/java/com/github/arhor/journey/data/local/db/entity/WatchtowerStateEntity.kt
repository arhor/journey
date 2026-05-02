package com.github.arhor.journey.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "watchtower_state")
data class WatchtowerStateEntity(
    @PrimaryKey
    val watchtowerId: String,
    val discoveredAt: Instant,
    val claimedAt: Instant?,
    val level: Int,
    val updatedAt: Instant,
)
