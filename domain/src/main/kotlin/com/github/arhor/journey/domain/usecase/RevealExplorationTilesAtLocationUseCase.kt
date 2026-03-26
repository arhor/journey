package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileGrid
import com.github.arhor.journey.domain.ExplorationTileRuntimeConfigHolder
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.repository.ExplorationTileRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RevealExplorationTilesAtLocationUseCase @Inject constructor(
    private val repository: ExplorationTileRepository,
    private val configHolder: ExplorationTileRuntimeConfigHolder,
) {
    suspend operator fun invoke(location: GeoPoint): Set<ExplorationTile> {
        val config = configHolder.snapshot()
        val tiles = ExplorationTileGrid.revealTilesAround(
            point = location,
            radiusMeters = config.revealRadiusMeters,
            zoom = config.canonicalZoom,
        )

        if (tiles.isNotEmpty()) {
            repository.markExplored(tiles)
        }

        return tiles
    }
}
