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
    ): Output<WatchtowerState, UpgradeWatchtowerError> {
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

        val result = transactionRunner.runInTransaction {
            val freshRecord = watchtowerRepository.getById(id)
                ?: return@runInTransaction Output.Failure(UpgradeWatchtowerError.NotFound(id))
            val freshState = freshRecord.state
            if (freshState?.claimedAt == null) {
                return@runInTransaction Output.Failure(UpgradeWatchtowerError.NotClaimed(id))
            }
            if (freshState.level >= WatchtowerBalance.MAX_LEVEL) {
                return@runInTransaction Output.Failure(UpgradeWatchtowerError.AlreadyAtMaxLevel(id))
            }

            val spent = heroInventoryRepository.spendAmount(
                heroId = hero.id,
                resourceTypeId = upgradeCost.resourceTypeId,
                amount = upgradeCost.amount,
                updatedAt = now,
            )
            if (spent == null) {
                return@runInTransaction Output.Failure(
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
                return@runInTransaction Output.Failure(UpgradeWatchtowerError.AlreadyAtMaxLevel(id))
            }

            val upgradedState = freshState.copy(
                level = upgradedLevel,
                updatedAt = now,
            )

            val config = getExplorationTileRuntimeConfig()
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

        return result
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
