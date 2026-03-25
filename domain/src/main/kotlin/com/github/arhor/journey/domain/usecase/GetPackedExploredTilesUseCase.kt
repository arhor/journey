package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.repository.ExplorationTileRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetPackedExploredTilesUseCase @Inject constructor(
    private val repository: ExplorationTileRepository,
) {
    suspend operator fun invoke(range: ExplorationTileRange): LongArray =
        repository.getPackedExploredTiles(range)
}
