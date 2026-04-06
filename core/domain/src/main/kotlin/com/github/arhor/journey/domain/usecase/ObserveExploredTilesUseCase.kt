package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.repository.ExplorationTileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveExploredTilesUseCase @Inject constructor(
    private val repository: ExplorationTileRepository,
) {
    operator fun invoke(range: ExplorationTileRange): Flow<Output<Set<MapTile>, UseCaseError>> =
        repository.observeExploredTiles(range).toUseCaseOutputFlow("observe explored tiles")
}
