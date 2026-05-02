package com.github.arhor.journey.domain.internal

import com.github.arhor.journey.domain.model.Watchtower
import com.github.arhor.journey.domain.model.WatchtowerPhase
import com.github.arhor.journey.domain.model.WatchtowerRecord

internal fun WatchtowerRecord.toWatchtower(): Watchtower? {
    val state = state ?: return null
    val isClaimed = state.claimedAt != null
    val phase = if (isClaimed) {
        WatchtowerPhase.CLAIMED
    } else {
        WatchtowerPhase.DISCOVERED_DORMANT
    }
    val level = state.level.takeIf { it > 0 }
    val nextLevel = level?.plus(1)
        ?.takeIf { it <= WatchtowerBalance.MAX_LEVEL }

    return Watchtower(
        id = definition.id,
        name = definition.name,
        description = definition.description,
        location = definition.location,
        interactionRadiusMeters = definition.interactionRadiusMeters,
        phase = phase,
        level = level,
        revealRadiusMeters = level?.let(WatchtowerBalance::revealRadiusMetersForLevel),
        claimCost = WatchtowerBalance.claimCost.takeIf { !isClaimed },
        nextUpgradeCost = nextLevel?.let(WatchtowerBalance::upgradeCostForLevel),
        nextRevealRadiusMeters = nextLevel?.let(WatchtowerBalance::revealRadiusMetersForLevel),
        canClaim = false,
        canUpgrade = false,
        distanceMeters = null,
    )
}
