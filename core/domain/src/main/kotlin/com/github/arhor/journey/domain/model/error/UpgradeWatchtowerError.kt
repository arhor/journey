package com.github.arhor.journey.domain.model.error

import com.github.arhor.journey.core.common.DomainError

sealed interface UpgradeWatchtowerError : DomainError {

    data class AlreadyAtMaxLevel(
        val watchtowerId: String,
    ) : UpgradeWatchtowerError

    data class NotFound(
        val watchtowerId: String,
    ) : UpgradeWatchtowerError

    data class NotClaimed(
        val watchtowerId: String,
    ) : UpgradeWatchtowerError

    data class NotInRange(
        val watchtowerId: String,
        val distanceMeters: Double,
        val interactionRadiusMeters: Double,
    ) : UpgradeWatchtowerError

    data class InsufficientResources(
        val watchtowerId: String,
        val resourceTypeId: String,
        val requiredAmount: Int,
        val availableAmount: Int,
    ) : UpgradeWatchtowerError

    data class Unexpected(
        override val cause: Throwable,
    ) : UpgradeWatchtowerError {
        override val message: String? = cause.message
    }
}
