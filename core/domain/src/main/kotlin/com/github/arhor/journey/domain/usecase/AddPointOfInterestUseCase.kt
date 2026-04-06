package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.PoiCategory
import com.github.arhor.journey.domain.model.PointOfInterest
import com.github.arhor.journey.domain.model.error.UseCaseError
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
    ): Output<Long, UseCaseError> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return invalidUseCaseInput("Point of interest name must not be blank.")
        }

        if (radiusMeters <= 0) {
            return invalidUseCaseInput("Point of interest radius must be greater than zero.")
        }

        return runSuspendingUseCaseCatching("add point of interest") {
            repository.upsert(
                PointOfInterest(
                    name = trimmedName,
                    description = description,
                    category = category,
                    location = location,
                    radiusMeters = radiusMeters,
                ),
            )
        }
    }
}
