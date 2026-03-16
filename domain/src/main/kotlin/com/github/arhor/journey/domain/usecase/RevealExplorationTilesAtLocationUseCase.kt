package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileGrid
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.repository.ExplorationTileRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RevealExplorationTilesAtLocationUseCase @Inject constructor(
    private val repository: ExplorationTileRepository,
) {
    suspend operator fun invoke(location: GeoPoint): Set<ExplorationTile> {
        val tiles = ExplorationTileGrid.revealTilesAround(location)

        if (tiles.isNotEmpty()) {
            repository.markExplored(tiles)
        }

        return tiles
    }
}
