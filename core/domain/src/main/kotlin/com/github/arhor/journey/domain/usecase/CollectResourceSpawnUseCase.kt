package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.TransactionRunner
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.ResourceSpawnCollectionResult
import com.github.arhor.journey.domain.repository.CollectedResourceSpawnRepository
import com.github.arhor.journey.domain.repository.HeroInventoryRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import com.github.arhor.journey.domain.repository.ResourceSpawnRepository
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
    ): ResourceSpawnCollectionResult {
        val collectedAt = clock.instant()

        return try {
            val hero = heroRepository.getCurrentHero()
            val spawn = resourceSpawnRepository.getActiveSpawn(
                spawnId = spawnId,
                at = collectedAt,
            ) ?: return ResourceSpawnCollectionResult.NotFound(spawnId)

            collectSpawn(
                heroId = hero.id,
                spawn = spawn,
                collectorLocation = collectorLocation,
                collectedAt = collectedAt,
            )
        } catch (e: Throwable) {
            ResourceSpawnCollectionResult.Failed(
                spawnId = spawnId,
                message = e.message,
            )
        }
    }

    internal suspend fun collectSpawn(
        heroId: String,
        spawn: ResourceSpawn,
        collectorLocation: GeoPoint,
        collectedAt: java.time.Instant,
    ): ResourceSpawnCollectionResult {
        val distanceMeters = collectorLocation.distanceTo(spawn.position)
        if (distanceMeters > spawn.collectionRadiusMeters) {
            return ResourceSpawnCollectionResult.NotCloseEnough(
                spawnId = spawn.id,
                distanceMeters = distanceMeters,
                collectionRadiusMeters = spawn.collectionRadiusMeters,
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
                ResourceSpawnCollectionResult.AlreadyCollected(spawn.id)
            } else {
                heroInventoryRepository.addAmount(
                    heroId = heroId,
                    resourceTypeId = spawn.typeId,
                    amount = 1,
                    updatedAt = collectedAt,
                )

                ResourceSpawnCollectionResult.Collected(
                    spawnId = spawn.id,
                    resourceTypeId = spawn.typeId,
                    amountAwarded = 1,
                )
            }
        }
    }
}
