package com.github.arhor.journey.domain.model

data class GeoPoint(
    val lat: Double,
    val lon: Double,
)

enum class PoiCategory {
    LANDMARK,
    SHRINE,
    DUNGEON,
    RESOURCE_NODE,
}

/**
 * A stable, domain-level representation of a destination/POI.
 *
 * This model intentionally contains only domain data; map SDK / rendering details belong to the UI layer.
 */
data class PointOfInterest(
    val id: String,
    val name: String,
    val description: String?,
    val category: PoiCategory,
    val location: GeoPoint,
    val radiusMeters: Int,
)

