package com.github.arhor.journey.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "breach_node_state",
    indices = [
        Index(value = ["h3CellId"], unique = true),
    ],
)
data class BreachNodeStateEntity(
    @PrimaryKey
    val breachNodeId: String,
    val h3CellId: String,
    val discoveredAt: Instant?,
    val controlledAt: Instant?,
    val lockdownUntil: Instant?,
    val updatedAt: Instant,
)
