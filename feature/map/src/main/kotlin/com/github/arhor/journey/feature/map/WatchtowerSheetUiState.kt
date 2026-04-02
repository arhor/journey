package com.github.arhor.journey.feature.map

import androidx.compose.runtime.Immutable

@Immutable
data class WatchtowerSheetUiState(
    val id: String,
    val title: String,
    val description: String?,
    val phase: WatchtowerSheetPhase,
    val level: Int?,
    val revealRadiusMeters: Int?,
    val nextRevealRadiusMeters: Int?,
    val distanceMeters: Int?,
    val claimCostLabel: String?,
    val upgradeCostLabel: String?,
    val canClaim: Boolean,
    val canUpgrade: Boolean,
    val claimDisabledReason: String?,
    val upgradeDisabledReason: String?,
    val isAtMaxLevel: Boolean,
)

enum class WatchtowerSheetPhase {
    DISCOVERED_DORMANT,
    CLAIMED,
}
