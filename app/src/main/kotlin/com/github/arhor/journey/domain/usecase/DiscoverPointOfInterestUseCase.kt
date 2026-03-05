package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.repository.ExplorationRepository
import java.time.Clock
import javax.inject.Inject

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

