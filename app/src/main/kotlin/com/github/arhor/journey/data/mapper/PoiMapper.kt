package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.PoiEntity
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.PoiCategory
import com.github.arhor.journey.domain.model.PointOfInterest

fun PoiEntity.toDomain(): PointOfInterest {
    val categoryEnum = runCatching { PoiCategory.valueOf(category) }.getOrDefault(PoiCategory.LANDMARK)
    return PointOfInterest(
        id = id,
        name = name,
        description = description,
        category = categoryEnum,
        location = GeoPoint(lat = lat, lon = lon),
        radiusMeters = radiusMeters,
    )
}

fun PointOfInterest.toEntity(): PoiEntity =
    PoiEntity(
        id = id,
        name = name,
        description = description,
        category = category.name,
        lat = location.lat,
        lon = location.lon,
        radiusMeters = radiusMeters,
    )

