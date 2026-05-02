package com.github.arhor.journey.domain.model.error

import com.github.arhor.journey.core.common.DomainError

sealed interface ClaimWatchtowerError : DomainError {

    data class AlreadyClaimed(
        val watchtowerId: String,
    ) : ClaimWatchtowerError

    data class NotFound(
        val watchtowerId: String,
    ) : ClaimWatchtowerError

    data class NotDiscovered(
        val watchtowerId: String,
    ) : ClaimWatchtowerError

    data class NotInRange(
        val watchtowerId: String,
        val distanceMeters: Double,
        val interactionRadiusMeters: Double,
    ) : ClaimWatchtowerError

    data class InsufficientResources(
        val watchtowerId: String,
        val resourceTypeId: String,
        val requiredAmount: Int,
        val availableAmount: Int,
    ) : ClaimWatchtowerError

    data class Unexpected(
        override val cause: Throwable,
    ) : ClaimWatchtowerError {
        override val message: String? = cause.message
    }
}
