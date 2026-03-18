package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.repository.ExplorationTileRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClearExplorationTileLightsUseCase @Inject constructor(
    private val repository: ExplorationTileRepository,
) {
    suspend operator fun invoke() {
        repository.clearExplorationTileLights()
    }
}
