package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.internal.tileAt
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.error.UseCaseError
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
    ): Output<Set<String>, UseCaseError> {
        if (newlyClearedTiles.isEmpty()) {
            return Output.Success(emptySet())
        }

        return runSuspendingUseCaseCatching("discover watchtowers by cleared tiles") {
            val discoveredAt = clock.instant()
            val config = when (val result = getExplorationTileRuntimeConfig()) {
                is Output.Success -> result.value
                is Output.Failure -> {
                    throw result.error.asThrowable("Failed to load exploration tile runtime config.")
                }
            }

            buildSet {
                repository.getIntersectingTiles(newlyClearedTiles).forEach { record ->
                    if (record.state != null) {
                        return@forEach
                    }

                    val watchtowerTile = tileAt(
                        point = record.definition.location,
                        zoom = config.canonicalZoom,
                    )
                    if (watchtowerTile in newlyClearedTiles && repository.markDiscovered(record.definition.id, discoveredAt)) {
                        add(record.definition.id)
                    }
                }
            }
        }
    }
}
