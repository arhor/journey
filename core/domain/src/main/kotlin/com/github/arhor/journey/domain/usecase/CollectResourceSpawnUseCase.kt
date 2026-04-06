package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.TransactionRunner
import com.github.arhor.journey.domain.model.CollectedResourceSpawnReward
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.error.CollectResourceSpawnError
import com.github.arhor.journey.domain.repository.CollectedResourceSpawnRepository
import com.github.arhor.journey.domain.repository.HeroInventoryRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import com.github.arhor.journey.domain.repository.ResourceSpawnRepository
import kotlinx.coroutines.CancellationException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectResourceSpawnUseCase @Inject constructor(
    private val heroRepository: HeroRepository,
    private val heroInventoryRepository: HeroInventoryRepository,
    private val collectedResourceSpawnRepository: CollectedResourceSpawnRepository,
    private val resourceSpawnRepository: ResourceSpawnRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        spawnId: String,
        collectorLocation: GeoPoint,
    ): Output<CollectedResourceSpawnReward, CollectResourceSpawnError> {
        val collectedAt = clock.instant()

        return try {
            val hero = heroRepository.getCurrentHero()
            val spawn = resourceSpawnRepository.getActiveSpawn(
                spawnId = spawnId,
                at = collectedAt,
            ) ?: return Output.Failure(CollectResourceSpawnError.NotFound(spawnId))

            collectSpawn(
                heroId = hero.id,
                spawn = spawn,
                collectorLocation = collectorLocation,
                collectedAt = collectedAt,
            )
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }

            Output.Failure(CollectResourceSpawnError.Unexpected(spawnId = spawnId, cause = e))
        }
    }

    internal suspend fun collectSpawn(
        heroId: String,
        spawn: ResourceSpawn,
        collectorLocation: GeoPoint,
        collectedAt: java.time.Instant,
    ): Output<CollectedResourceSpawnReward, CollectResourceSpawnError> {
        val distanceMeters = collectorLocation.distanceTo(spawn.position)
        if (distanceMeters > spawn.collectionRadiusMeters) {
            return Output.Failure(
                CollectResourceSpawnError.NotCloseEnough(
                    spawnId = spawn.id,
                    distanceMeters = distanceMeters,
                    collectionRadiusMeters = spawn.collectionRadiusMeters,
                ),
            )
        }

        return transactionRunner.runInTransaction {
            val collectedMarkerInserted = collectedResourceSpawnRepository.markCollected(
                heroId = heroId,
                spawnId = spawn.id,
                resourceTypeId = spawn.typeId,
                collectedAt = collectedAt,
            )

            if (!collectedMarkerInserted) {
                Output.Failure(CollectResourceSpawnError.AlreadyCollected(spawn.id))
            } else {
                heroInventoryRepository.addAmount(
                    heroId = heroId,
                    resourceTypeId = spawn.typeId,
                    amount = 1,
                    updatedAt = collectedAt,
                )

                Output.Success(
                    CollectedResourceSpawnReward(
                        spawnId = spawn.id,
                        resourceTypeId = spawn.typeId,
                        amountAwarded = 1,
                    ),
                )
            }
        }
    }
}
