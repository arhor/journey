package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.TransactionRunner
import com.github.arhor.journey.domain.internal.WatchtowerBalance
import com.github.arhor.journey.domain.internal.revealTilesAround
import com.github.arhor.journey.domain.internal.tileAt
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.WatchtowerState
import com.github.arhor.journey.domain.model.error.UpgradeWatchtowerError
import com.github.arhor.journey.domain.repository.HeroInventoryRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import com.github.arhor.journey.domain.repository.WatchtowerRepository
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpgradeWatchtowerUseCase @Inject constructor(
    private val heroRepository: HeroRepository,
    private val heroInventoryRepository: HeroInventoryRepository,
    private val watchtowerRepository: WatchtowerRepository,
    private val transactionRunner: TransactionRunner,
    private val getExplorationTileRuntimeConfig: GetExplorationTileRuntimeConfigUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        id: String,
        actorLocation: GeoPoint,
    ): Output<WatchtowerState, UpgradeWatchtowerError> = try {
        val existing = watchtowerRepository.getById(id)
            ?: return Output.Failure(UpgradeWatchtowerError.NotFound(id))
        val state = existing.state
        if (state?.claimedAt == null) {
            return Output.Failure(UpgradeWatchtowerError.NotClaimed(id))
        }
        if (state.level >= WatchtowerBalance.MAX_LEVEL) {
            return Output.Failure(UpgradeWatchtowerError.AlreadyAtMaxLevel(id))
        }

        val distanceMeters = actorLocation.distanceTo(existing.definition.location)
        if (distanceMeters > existing.definition.interactionRadiusMeters) {
            return Output.Failure(
                UpgradeWatchtowerError.NotInRange(
                    watchtowerId = id,
                    distanceMeters = distanceMeters,
                    interactionRadiusMeters = existing.definition.interactionRadiusMeters,
                ),
            )
        }

        val nextLevel = state.level + 1
        val upgradeCost = requireNotNull(WatchtowerBalance.upgradeCostForLevel(nextLevel))
        val hero = heroRepository.getCurrentHero()
        val now = clock.instant()
        val config = when (val result = getExplorationTileRuntimeConfig()) {
            is Output.Success -> result.value
            is Output.Failure -> {
                return Output.Failure(
                    UpgradeWatchtowerError.Unexpected(
                        result.error.asThrowable("Failed to load exploration tile runtime config."),
                    ),
                )
            }
        }

        transactionRunner.runInTransaction {
            val freshRecord = watchtowerRepository.getById(id)
                ?: abortTransaction(UpgradeWatchtowerError.NotFound(id))
            val freshState = freshRecord.state
            if (freshState?.claimedAt == null) {
                abortTransaction(UpgradeWatchtowerError.NotClaimed(id))
            }
            if (freshState.level >= WatchtowerBalance.MAX_LEVEL) {
                abortTransaction(UpgradeWatchtowerError.AlreadyAtMaxLevel(id))
            }

            val spent = heroInventoryRepository.spendAmount(
                heroId = hero.id,
                resourceTypeId = upgradeCost.resourceTypeId,
                amount = upgradeCost.amount,
                updatedAt = now,
            )
            if (spent == null) {
                abortTransaction(
                    UpgradeWatchtowerError.InsufficientResources(
                        watchtowerId = id,
                        resourceTypeId = upgradeCost.resourceTypeId,
                        requiredAmount = upgradeCost.amount,
                        availableAmount = heroInventoryRepository.getAmount(
                            heroId = hero.id,
                            resourceTypeId = upgradeCost.resourceTypeId,
                        ),
                    ),
                )
            }

            val upgradedLevel = freshState.level + 1
            if (!watchtowerRepository.setLevel(id = id, level = upgradedLevel, updatedAt = now)) {
                abortTransaction(UpgradeWatchtowerError.AlreadyAtMaxLevel(id))
            }

            val upgradedState = freshState.copy(
                level = upgradedLevel,
                updatedAt = now,
            )
            val revealedTiles = revealTilesAround(
                point = freshRecord.definition.location,
                radiusMeters = WatchtowerBalance.revealRadiusMetersForLevel(level = upgradedLevel),
                zoom = config.canonicalZoom,
            )
            revealDormantWatchtowers(
                revealedTiles = revealedTiles,
                discoveredAt = now,
                canonicalZoom = config.canonicalZoom,
            )

            Output.Success(upgradedState)
        }
    } catch (exception: TransactionAbortException) {
        @Suppress("UNCHECKED_CAST")
        Output.Failure(exception.error as UpgradeWatchtowerError)
    } catch (exception: Throwable) {
        if (exception is CancellationException) {
            throw exception
        }

        Output.Failure(UpgradeWatchtowerError.Unexpected(exception))
    }

    private suspend fun revealDormantWatchtowers(
        revealedTiles: Set<MapTile>,
        discoveredAt: Instant,
        canonicalZoom: Int,
    ) {
        watchtowerRepository.getIntersectingTiles(revealedTiles).forEach { record ->
            if (record.state != null) {
                return@forEach
            }

            val towerTile = tileAt(
                point = record.definition.location,
                zoom = canonicalZoom,
            )
            if (towerTile in revealedTiles) {
                watchtowerRepository.markDiscovered(record.definition.id, discoveredAt)
            }
        }
    }
}
