package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.PointOfInterest
import com.github.arhor.journey.domain.repository.PointOfInterestRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObservePointsOfInterestUseCase @Inject constructor(
    private val repository: PointOfInterestRepository,
) {
    operator fun invoke(): Flow<List<PointOfInterest>> =
        repository.observeAll()
            .onStart { repository.ensureSeeded() }
}
