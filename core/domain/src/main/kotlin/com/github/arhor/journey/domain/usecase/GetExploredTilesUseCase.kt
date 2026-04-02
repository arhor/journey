package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.repository.ExplorationTileRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetExploredTilesUseCase @Inject constructor(
    private val repository: ExplorationTileRepository,
) {
    suspend operator fun invoke(range: ExplorationTileRange): Output<Set<MapTile>, UseCaseError> =
        runSuspendingUseCaseCatching("get explored tiles") {
            repository.getExploredTiles(range)
        }
}
