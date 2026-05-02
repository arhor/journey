package com.github.arhor.journey.domain.model.error

import com.github.arhor.journey.core.common.DomainError

sealed interface CollectResourceSpawnError : DomainError {

    data class AlreadyCollected(
        val spawnId: String,
    ) : CollectResourceSpawnError

    data class NotCloseEnough(
        val spawnId: String,
        val distanceMeters: Double,
        val collectionRadiusMeters: Double,
    ) : CollectResourceSpawnError

    data class NotFound(
        val spawnId: String,
    ) : CollectResourceSpawnError

    data class Unexpected(
        val spawnId: String,
        override val cause: Throwable,
    ) : CollectResourceSpawnError {
        override val message: String? = cause.message
    }
}
