package com.github.arhor.journey.domain.model

sealed interface ResourceSpawnCollectionResult {

    data class Collected(
        val spawnId: String,
        val resourceTypeId: String,
        val amountAwarded: Int,
    ) : ResourceSpawnCollectionResult

    data class AlreadyCollected(
        val spawnId: String,
    ) : ResourceSpawnCollectionResult

    data class NotCloseEnough(
        val spawnId: String,
        val distanceMeters: Double,
        val collectionRadiusMeters: Double,
    ) : ResourceSpawnCollectionResult

    data class NotFound(
        val spawnId: String,
    ) : ResourceSpawnCollectionResult

    data class Failed(
        val spawnId: String,
        val message: String? = null,
    ) : ResourceSpawnCollectionResult
}
