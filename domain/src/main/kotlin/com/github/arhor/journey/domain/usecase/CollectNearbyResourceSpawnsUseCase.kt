package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.ResourceSpawnCollectionResult
import com.github.arhor.journey.domain.model.ResourceSpawnQuery
import com.github.arhor.journey.domain.repository.HeroRepository
import com.github.arhor.journey.domain.repository.ResourceSpawnRepository
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
    suspend operator fun invoke(location: GeoPoint): List<ResourceSpawnCollectionResult> {
        val collectedAt = clock.instant()
        val hero = heroRepository.getCurrentHero()
        val nearbySpawns = resourceSpawnRepository.getActiveSpawns(
            ResourceSpawnQuery(
                at = collectedAt,
                center = location,
                radiusMeters = NEARBY_RESOURCE_SPAWN_SCAN_RADIUS_METERS,
            ),
        )

        return nearbySpawns.map { spawn ->
            try {
                collectResourceSpawn.collectSpawn(
                    heroId = hero.id,
                    spawn = spawn,
                    collectorLocation = location,
                    collectedAt = collectedAt,
                )
            } catch (e: Throwable) {
                ResourceSpawnCollectionResult.Failed(
                    spawnId = spawn.id,
                    message = e.message,
                )
            }
        }
    }
}
