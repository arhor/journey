package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.ExplorationTileLight
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.repository.ExplorationTileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveExplorationTileLightsUseCase @Inject constructor(
    private val repository: ExplorationTileRepository,
) {
    operator fun invoke(range: ExplorationTileRange): Flow<List<ExplorationTileLight>> =
        repository.observeExplorationTileLights(range)
}
