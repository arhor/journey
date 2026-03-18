package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.ExplorationTileGrid
import com.github.arhor.journey.domain.model.ExplorationTileLight
import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfigHolder
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.repository.ExplorationTileRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApplyPlayerExplorationLightAtLocationUseCase @Inject constructor(
    private val repository: ExplorationTileRepository,
    private val configHolder: ExplorationTileRuntimeConfigHolder,
) {
    suspend operator fun invoke(location: GeoPoint): Set<ExplorationTileLight> {
        val tileLights = ExplorationTileGrid.playerLightContributionsAt(
            point = location,
            zoom = configHolder.snapshot().canonicalZoom,
        )

        if (tileLights.isNotEmpty()) {
            repository.accumulateExplorationTileLights(tileLights)
        }

        return tileLights
    }
}
