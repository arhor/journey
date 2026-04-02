package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.ExplorationTileRuntimeConfigHolder
import com.github.arhor.journey.domain.internal.revealTilesAround
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.repository.ExplorationTileRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RevealExplorationTilesAtLocationUseCase @Inject constructor(
    private val repository: ExplorationTileRepository,
    private val configHolder: ExplorationTileRuntimeConfigHolder,
) {
    suspend operator fun invoke(location: GeoPoint): Output<Set<MapTile>, UseCaseError> =
        runSuspendingUseCaseCatching("reveal exploration tiles at location") {
            val config = configHolder.snapshot()
            val tiles = revealTilesAround(
                point = location,
                radiusMeters = config.revealRadiusMeters,
                zoom = config.canonicalZoom,
            )

            if (tiles.isEmpty()) {
                emptySet()
            } else {
                repository.markExplored(tiles)
            }
        }
}
