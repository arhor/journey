package com.github.arhor.journey.domain.model

/**
 * A stable, domain-level representation of a destination/POI.
 *
 * This model intentionally contains only domain data; map SDK / rendering details belong to the UI layer.
 */
data class PointOfInterest(
    val id: Long = 0,
    val name: String,
    val description: String?,
    val category: PoiCategory,
    val location: GeoPoint,
    val radiusMeters: Int,
)
