package com.github.arhor.journey.domain.model

data class Watchtower(
    val id: String,
    val name: String,
    val description: String?,
    val location: GeoPoint,
    val interactionRadiusMeters: Double,
    val phase: WatchtowerPhase,
    val level: Int?,
    val revealRadiusMeters: Double?,
    val claimCost: WatchtowerResourceCost?,
    val nextUpgradeCost: WatchtowerResourceCost?,
    val nextRevealRadiusMeters: Double?,
    val canClaim: Boolean,
    val canUpgrade: Boolean,
    val distanceMeters: Double?,
)

enum class WatchtowerPhase {
    DISCOVERED_DORMANT,
    CLAIMED,
}
