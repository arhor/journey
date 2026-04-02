package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.ExplorationTileRuntimeConfigHolder
import com.github.arhor.journey.domain.internal.revealTilesAround
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.repository.ExplorationTileRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RevealExplorationTilesAtLocationUseCase @Inject constructor(
    private val repository: ExplorationTileRepository,
    private val configHolder: ExplorationTileRuntimeConfigHolder,
) {
    suspend operator fun invoke(location: GeoPoint): Set<MapTile> {
        val config = configHolder.snapshot()
        val tiles = revealTilesAround(
            point = location,
            radiusMeters = config.revealRadiusMeters,
            zoom = config.canonicalZoom,
        )

        if (tiles.isEmpty()) {
            return emptySet()
        }

        return repository.markExplored(tiles)
    }
}
