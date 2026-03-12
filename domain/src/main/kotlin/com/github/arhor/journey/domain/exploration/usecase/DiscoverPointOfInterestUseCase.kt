package com.github.arhor.journey.domain.exploration.usecase

import com.github.arhor.journey.domain.exploration.repository.ExplorationRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiscoverPointOfInterestUseCase @Inject constructor(
    private val repository: ExplorationRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(poiId: String) {
        repository.discoverPoi(
            poiId = poiId,
            discoveredAt = clock.instant(),
        )
    }
}
