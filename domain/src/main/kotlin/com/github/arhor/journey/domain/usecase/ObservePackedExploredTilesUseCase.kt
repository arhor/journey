package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.repository.ExplorationTileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObservePackedExploredTilesUseCase @Inject constructor(
    private val repository: ExplorationTileRepository,
) {
    operator fun invoke(range: ExplorationTileRange): Flow<LongArray> =
        repository.observePackedExploredTiles(range)
}
