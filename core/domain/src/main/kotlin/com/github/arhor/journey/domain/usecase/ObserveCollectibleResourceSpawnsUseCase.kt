package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.ResourceSpawnQuery
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.repository.CollectedResourceSpawnRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import com.github.arhor.journey.domain.repository.ResourceSpawnRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
        combine(
            heroRepository.observeCurrentHero(),
            activeResourceQueryInstants(),
        ) { hero, activeAt -> hero to activeAt }
            .flatMapLatest { (hero, activeAt) ->
                combine(
                    resourceSpawnRepository.observeActiveSpawns(
                        ResourceSpawnQuery(
                            at = activeAt,
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

    private fun activeResourceQueryInstants(): Flow<Instant> =
        flow {
            while (true) {
                val activeAt = clock.instant()
                emit(activeAt)

                val nextUtcMidnight = activeAt.toUtcLocalDate()
                    .plusDays(1)
                    .atStartOfDay()
                    .toInstant(ZoneOffset.UTC)
                val delayMillis = (nextUtcMidnight.toEpochMilli() - activeAt.toEpochMilli())
                    .coerceAtLeast(1L)
                delay(delayMillis)
            }
        }
            .distinctUntilChangedBy { it.toUtcLocalDate() }

    private fun Instant.toUtcLocalDate(): LocalDate =
        atZone(ZoneOffset.UTC).toLocalDate()
}
