package com.github.arhor.journey.domain.model

import java.time.Instant

/**
 * A collectible resource spawn currently active on the world map.
 *
 * This model intentionally contains only domain data; rendering concerns belong to the UI layer.
 */
data class ResourceSpawn(
    val id: String,
    val typeId: String,
    val position: GeoPoint,
    val collectionRadiusMeters: Double,
    val availableFrom: Instant? = null,
    val availableUntil: Instant? = null,
)
