package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.WatchtowerStateEntity
import com.github.arhor.journey.domain.model.WatchtowerState

fun WatchtowerStateEntity.toDomain(): WatchtowerState =
    WatchtowerState(
        watchtowerId = watchtowerId,
        discoveredAt = discoveredAt,
        claimedAt = claimedAt,
        level = level,
        updatedAt = updatedAt,
    )
