package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.ResourceSpawnQuery
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.repository.CollectedResourceSpawnRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import com.github.arhor.journey.domain.repository.ResourceSpawnRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveCollectibleResourceSpawnsUseCase @Inject constructor(
    private val heroRepository: HeroRepository,
    private val collectedResourceSpawnRepository: CollectedResourceSpawnRepository,
    private val resourceSpawnRepository: ResourceSpawnRepository,
    private val clock: Clock,
) {
    operator fun invoke(bounds: GeoBounds): Flow<Output<List<ResourceSpawn>, UseCaseError>> =
        heroRepository.observeCurrentHero()
            .flatMapLatest { hero ->
                combine(
                    resourceSpawnRepository.observeActiveSpawns(
                        ResourceSpawnQuery(
                            at = clock.instant(),
                            bounds = bounds,
                        ),
                    ),
                    collectedResourceSpawnRepository.observeAll(hero.id),
                ) { activeSpawns, collectedSpawns ->
                    val collectedSpawnIds = collectedSpawns
                        .mapTo(mutableSetOf()) { it.spawnId }

                    activeSpawns.filterNot { spawn -> spawn.id in collectedSpawnIds }
                }
            }
            .toUseCaseOutputFlow("observe collectible resource spawns")
}
