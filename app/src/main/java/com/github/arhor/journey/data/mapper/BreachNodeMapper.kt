package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.BreachNodeStateEntity
import com.github.arhor.journey.domain.model.BreachNodeDefinition
import com.github.arhor.journey.domain.model.BreachNodeRecord
import com.github.arhor.journey.domain.model.BreachNodeState

fun BreachNodeStateEntity.toDomain(): BreachNodeState =
    BreachNodeState(
        breachNodeId = breachNodeId,
        h3CellId = h3CellId,
        discoveredAt = discoveredAt,
        controlledAt = controlledAt,
        lockdownUntil = lockdownUntil,
        updatedAt = updatedAt,
    )

fun BreachNodeState.toEntity(): BreachNodeStateEntity =
    BreachNodeStateEntity(
        breachNodeId = breachNodeId,
        h3CellId = h3CellId,
        discoveredAt = discoveredAt,
        controlledAt = controlledAt,
        lockdownUntil = lockdownUntil,
        updatedAt = updatedAt,
    )

fun BreachNodeDefinition.toRecord(state: BreachNodeState?): BreachNodeRecord =
    BreachNodeRecord(
        definition = this,
        state = state,
    )
