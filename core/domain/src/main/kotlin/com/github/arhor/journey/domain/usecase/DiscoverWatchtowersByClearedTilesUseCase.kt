package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.internal.tileAt
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.repository.WatchtowerRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiscoverWatchtowersByClearedTilesUseCase @Inject constructor(
    private val repository: WatchtowerRepository,
    private val getExplorationTileRuntimeConfig: GetExplorationTileRuntimeConfigUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        newlyClearedTiles: Set<MapTile>,
    ): Set<String> {
        if (newlyClearedTiles.isEmpty()) {
            return emptySet()
        }

        val discoveredAt = clock.instant()
        val canonicalZoom = getExplorationTileRuntimeConfig().canonicalZoom

        return buildSet {
            repository.getIntersectingTiles(newlyClearedTiles).forEach { record ->
                if (record.state != null) {
                    return@forEach
                }

                val watchtowerTile = tileAt(
                    point = record.definition.location,
                    zoom = canonicalZoom,
                )
                if (watchtowerTile in newlyClearedTiles && repository.markDiscovered(record.definition.id, discoveredAt)) {
                    add(record.definition.id)
                }
            }
        }
    }
}
