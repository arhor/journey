package com.github.arhor.journey.data.local.seed

import com.github.arhor.journey.domain.exploration.model.GeoPoint
import com.github.arhor.journey.domain.exploration.model.PoiCategory
import com.github.arhor.journey.domain.exploration.model.PointOfInterest

/**
 * Small, local-only demo set of POIs.
 *
 * These are meant to provide stable ids for early development and tests. Real content generation,
 * remote sync, or user-created POIs are intentionally deferred.
 */
object PointOfInterestSeed {
    val items: List<PointOfInterest> = listOf(
        PointOfInterest(
            id = "poi_shrine_old_oak",
            name = "Shrine of the Old Oak",
            description = "A quiet shrine tucked away under an ancient tree.",
            category = PoiCategory.SHRINE,
            location = GeoPoint(lat = 52.2297, lon = 21.0122),
            radiusMeters = 50,
        ),
        PointOfInterest(
            id = "poi_landmark_river_gate",
            name = "River Gate",
            description = "A landmark where travelers cross into the unknown.",
            category = PoiCategory.LANDMARK,
            location = GeoPoint(lat = 52.2311, lon = 21.0180),
            radiusMeters = 60,
        ),
        PointOfInterest(
            id = "poi_dungeon_blackwell",
            name = "Blackwell Depths",
            description = "A rumored dungeon entrance near a forgotten well.",
            category = PoiCategory.DUNGEON,
            location = GeoPoint(lat = 52.2270, lon = 21.0105),
            radiusMeters = 80,
        ),
    )
}

