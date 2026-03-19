package com.github.arhor.journey.domain.model

import java.time.Instant

data class ResourceSpawnQuery(
    val at: Instant,
    val bounds: GeoBounds? = null,
    val center: GeoPoint? = null,
    val radiusMeters: Double? = null,
) {
    init {
        require(bounds != null || center != null) {
            "ResourceSpawnQuery requires bounds or center."
        }
        require(radiusMeters == null || radiusMeters >= 0.0) {
            "ResourceSpawnQuery radiusMeters must not be negative."
        }
        require(center != null || radiusMeters == null) {
            "ResourceSpawnQuery radiusMeters requires center."
        }
    }
}
