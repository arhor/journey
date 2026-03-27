package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.PoiCategory
import com.github.arhor.journey.domain.model.PointOfInterest
import com.github.arhor.journey.domain.repository.PointOfInterestRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddPointOfInterestUseCase @Inject constructor(
    private val repository: PointOfInterestRepository,
) {
    suspend operator fun invoke(
        name: String,
        description: String?,
        category: PoiCategory,
        location: GeoPoint,
        radiusMeters: Int,
    ): Long {
        val id = repository.upsert(
            PointOfInterest(
                name = name,
                description = description,
                category = category,
                location = location,
                radiusMeters = radiusMeters,
            ),
        )
        return id
    }
}
