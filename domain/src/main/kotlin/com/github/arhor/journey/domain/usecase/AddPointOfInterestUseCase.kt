package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.PoiCategory
import com.github.arhor.journey.domain.model.PointOfInterest
import com.github.arhor.journey.domain.repository.PointOfInterestRepository
import java.util.UUID
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
    ): String {
        val id = "poi_${UUID.randomUUID().toString().replace('-', '_')}"

        repository.upsert(
            PointOfInterest(
                id = id,
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
