package com.github.arhor.journey.feature.map.presentation

import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.Watchtower
import com.github.arhor.journey.domain.model.WatchtowerPhase
import com.github.arhor.journey.domain.model.WatchtowerResourceCost
import com.github.arhor.journey.feature.map.WatchtowerSheetPhase
import com.github.arhor.journey.feature.map.WatchtowerSheetUiState
import javax.inject.Inject
import kotlin.math.roundToInt

class SelectedWatchtowerPresenter @Inject constructor() {

    fun withInteractionContext(
        watchtower: Watchtower,
        actorLocation: GeoPoint?,
        resourceAmounts: Map<String, Int>,
    ): Watchtower {
        val distanceMeters = actorLocation?.distanceTo(watchtower.location)
        val isInRange = distanceMeters != null && distanceMeters <= watchtower.interactionRadiusMeters
        val claimAffordable = watchtower.claimCost?.isAffordable(resourceAmounts) ?: true
        val upgradeAffordable = watchtower.nextUpgradeCost?.isAffordable(resourceAmounts) ?: true

        return watchtower.copy(
            canClaim = watchtower.phase == WatchtowerPhase.DISCOVERED_DORMANT && isInRange && claimAffordable,
            canUpgrade = watchtower.phase == WatchtowerPhase.CLAIMED &&
                watchtower.nextUpgradeCost != null &&
                isInRange &&
                upgradeAffordable,
            distanceMeters = distanceMeters,
        )
    }

    fun present(
        watchtower: Watchtower,
        resourceAmounts: Map<String, Int>,
    ): WatchtowerSheetUiState {
        val distanceMeters = watchtower.distanceMeters
        val nextUpgradeCost = watchtower.nextUpgradeCost
        val isInRange = distanceMeters != null && distanceMeters <= watchtower.interactionRadiusMeters
        val claimAffordable = watchtower.claimCost?.isAffordable(resourceAmounts) ?: true
        val upgradeAffordable = nextUpgradeCost?.isAffordable(resourceAmounts) ?: true

        return WatchtowerSheetUiState(
            id = watchtower.id,
            title = watchtower.name,
            description = watchtower.description,
            phase = when (watchtower.phase) {
                WatchtowerPhase.DISCOVERED_DORMANT -> WatchtowerSheetPhase.DISCOVERED_DORMANT
                WatchtowerPhase.CLAIMED -> WatchtowerSheetPhase.CLAIMED
            },
            level = watchtower.level,
            revealRadiusMeters = watchtower.revealRadiusMeters?.roundToInt(),
            nextRevealRadiusMeters = watchtower.nextRevealRadiusMeters?.roundToInt(),
            distanceMeters = distanceMeters?.roundToInt(),
            claimCostLabel = watchtower.claimCost?.toDisplayLabel(),
            upgradeCostLabel = nextUpgradeCost?.toDisplayLabel(),
            canClaim = watchtower.canClaim && claimAffordable,
            canUpgrade = watchtower.canUpgrade && upgradeAffordable,
            claimDisabledReason = when {
                watchtower.phase != WatchtowerPhase.DISCOVERED_DORMANT -> null
                isInRange && !claimAffordable -> resourceRequirementMessage(watchtower.claimCost.resourceTypeId)
                isInRange -> null
                distanceMeters != null -> WATCHTOWER_MOVE_CLOSER_MESSAGE
                else -> CURRENT_LOCATION_UNAVAILABLE_MESSAGE
            },
            upgradeDisabledReason = when {
                watchtower.phase != WatchtowerPhase.CLAIMED || nextUpgradeCost == null -> null
                isInRange && !upgradeAffordable -> resourceRequirementMessage(nextUpgradeCost.resourceTypeId)
                isInRange -> null
                distanceMeters != null -> WATCHTOWER_MOVE_CLOSER_MESSAGE
                else -> CURRENT_LOCATION_UNAVAILABLE_MESSAGE
            },
            isAtMaxLevel = watchtower.phase == WatchtowerPhase.CLAIMED && nextUpgradeCost == null,
        )
    }

    fun resourceRequirementMessage(resourceTypeId: String?): String {
        val fallbackTypeId = resourceTypeId ?: "materials"
        val resourceLabel = ResourceType.fromTypeId(fallbackTypeId)?.displayName ?: fallbackTypeId
        return "Not enough $resourceLabel."
    }

    private fun WatchtowerResourceCost.toDisplayLabel(): String {
        val resourceLabel = ResourceType.fromTypeId(resourceTypeId)?.displayName ?: resourceTypeId
        return "$amount $resourceLabel"
    }

    private fun WatchtowerResourceCost.isAffordable(resourceAmounts: Map<String, Int>): Boolean =
        (resourceAmounts[resourceTypeId] ?: 0) >= amount

    private companion object {
        const val CURRENT_LOCATION_UNAVAILABLE_MESSAGE = "Current location is not available yet."
        const val WATCHTOWER_MOVE_CLOSER_MESSAGE = "Move closer to interact."
    }
}
