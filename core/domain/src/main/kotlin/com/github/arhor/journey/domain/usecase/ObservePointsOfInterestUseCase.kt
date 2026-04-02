package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.PointOfInterest
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.repository.PointOfInterestRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObservePointsOfInterestUseCase @Inject constructor(
    private val repository: PointOfInterestRepository,
) {
    operator fun invoke(): Flow<Output<List<PointOfInterest>, UseCaseError>> =
        repository.observeAll().toUseCaseOutputFlow("observe points of interest")
}
