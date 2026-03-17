package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.PointOfInterest
import com.github.arhor.journey.domain.repository.PointOfInterestRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetPointOfInterestUseCase @Inject constructor(
    private val repository: PointOfInterestRepository,
) {
    suspend operator fun invoke(id: String): PointOfInterest? =
        repository.getById(id)
}
