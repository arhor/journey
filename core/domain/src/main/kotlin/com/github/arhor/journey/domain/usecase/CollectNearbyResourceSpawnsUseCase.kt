package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.CollectedResourceSpawnReward
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.ResourceSpawnQuery
import com.github.arhor.journey.domain.model.error.CollectResourceSpawnError
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.repository.HeroRepository
import com.github.arhor.journey.domain.repository.ResourceSpawnRepository
import kotlinx.coroutines.CancellationException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

private const val NEARBY_RESOURCE_SPAWN_SCAN_RADIUS_METERS = 100.0

@Singleton
class CollectNearbyResourceSpawnsUseCase @Inject constructor(
    private val heroRepository: HeroRepository,
    private val resourceSpawnRepository: ResourceSpawnRepository,
    private val collectResourceSpawn: CollectResourceSpawnUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        location: GeoPoint,
    ): Output<List<Output<CollectedResourceSpawnReward, CollectResourceSpawnError>>, UseCaseError> =
        runSuspendingUseCaseCatching("collect nearby resource spawns") {
            val collectedAt = clock.instant()
            val hero = heroRepository.getCurrentHero()
            val nearbySpawns = resourceSpawnRepository.getActiveSpawns(
                ResourceSpawnQuery(
                    at = collectedAt,
                    center = location,
                    radiusMeters = NEARBY_RESOURCE_SPAWN_SCAN_RADIUS_METERS,
                ),
            )

            nearbySpawns.map { spawn ->
                try {
                    collectResourceSpawn.collectSpawn(
                        heroId = hero.id,
                        spawn = spawn,
                        collectorLocation = location,
                        collectedAt = collectedAt,
                    )
                } catch (exception: Throwable) {
                    if (exception is CancellationException) {
                        throw exception
                    }

                    Output.Failure(
                        CollectResourceSpawnError.Unexpected(
                            spawnId = spawn.id,
                            cause = exception,
                        ),
                    )
                }
            }
        }
}
