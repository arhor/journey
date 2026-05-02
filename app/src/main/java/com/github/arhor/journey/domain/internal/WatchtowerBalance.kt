package com.github.arhor.journey.domain.internal

import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.domain.model.WatchtowerResourceCost

internal object WatchtowerBalance {
    const val DEFAULT_INTERACTION_RADIUS_METERS = WatchtowerGeneration.INTERACTION_RADIUS_METERS
    const val MAX_LEVEL = 3
    const val MAX_REVEAL_RADIUS_METERS = 400.0

    val claimCost = WatchtowerResourceCost(
        resourceTypeId = ResourceType.SCRAP.typeId,
        amount = 5,
    )

    fun revealRadiusMetersForLevel(level: Int): Double = when (level) {
        1 -> 150.0
        2 -> 250.0
        3 -> 400.0
        else -> error("Unsupported Watchtower level: $level")
    }

    fun upgradeCostForLevel(level: Int): WatchtowerResourceCost? = when (level) {
        2 -> WatchtowerResourceCost(
            resourceTypeId = ResourceType.COMPONENTS.typeId,
            amount = 10,
        )

        3 -> WatchtowerResourceCost(
            resourceTypeId = ResourceType.COMPONENTS.typeId,
            amount = 15,
        )

        else -> null
    }
}
