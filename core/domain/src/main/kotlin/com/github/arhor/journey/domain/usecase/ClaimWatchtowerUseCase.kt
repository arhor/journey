package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.TransactionRunner
import com.github.arhor.journey.domain.internal.WatchtowerBalance
import com.github.arhor.journey.domain.internal.revealTilesAround
import com.github.arhor.journey.domain.internal.tileAt
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.WatchtowerState
import com.github.arhor.journey.domain.model.error.ClaimWatchtowerError
import com.github.arhor.journey.domain.repository.HeroInventoryRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import com.github.arhor.journey.domain.repository.WatchtowerRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClaimWatchtowerUseCase @Inject constructor(
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
    ): Output<WatchtowerState, ClaimWatchtowerError> {
        val existing = watchtowerRepository.getById(id)
            ?: return Output.Failure(ClaimWatchtowerError.NotFound(id))
        val state = existing.state
            ?: return Output.Failure(ClaimWatchtowerError.NotDiscovered(id))
        if (state.claimedAt != null) {
            return Output.Failure(ClaimWatchtowerError.AlreadyClaimed(id))
        }

        val distanceMeters = actorLocation.distanceTo(existing.definition.location)
        if (distanceMeters > existing.definition.interactionRadiusMeters) {
            return Output.Failure(
                ClaimWatchtowerError.NotInRange(
                    watchtowerId = id,
                    distanceMeters = distanceMeters,
                    interactionRadiusMeters = existing.definition.interactionRadiusMeters,
                ),
            )
        }

        val hero = heroRepository.getCurrentHero()
        val now = clock.instant()
        val claimCost = WatchtowerBalance.claimCost

        val result = transactionRunner.runInTransaction {
            val freshRecord = watchtowerRepository.getById(id)
                ?: return@runInTransaction Output.Failure(ClaimWatchtowerError.NotFound(id))
            val freshState = freshRecord.state
                ?: return@runInTransaction Output.Failure(ClaimWatchtowerError.NotDiscovered(id))
            if (freshState.claimedAt != null) {
                return@runInTransaction Output.Failure(ClaimWatchtowerError.AlreadyClaimed(id))
            }

            val spent = heroInventoryRepository.spendAmount(
                heroId = hero.id,
                resourceTypeId = claimCost.resourceTypeId,
                amount = claimCost.amount,
                updatedAt = now,
            )
            if (spent == null) {
                return@runInTransaction Output.Failure(
                    ClaimWatchtowerError.InsufficientResources(
                        watchtowerId = id,
                        resourceTypeId = claimCost.resourceTypeId,
                        requiredAmount = claimCost.amount,
                        availableAmount = heroInventoryRepository.getAmount(
                            heroId = hero.id,
                            resourceTypeId = claimCost.resourceTypeId,
                        ),
                    ),
                )
            }

            if (!watchtowerRepository.markClaimed(id = id, claimedAt = now, level = 1, updatedAt = now)) {
                return@runInTransaction Output.Failure(ClaimWatchtowerError.AlreadyClaimed(id))
            }

            val claimedState = freshState.copy(
                claimedAt = now,
                level = 1,
                updatedAt = now,
            )

            val config = getExplorationTileRuntimeConfig()
            val revealedTiles = revealTilesAround(
                point = freshRecord.definition.location,
                radiusMeters = WatchtowerBalance.revealRadiusMetersForLevel(level = 1),
                zoom = config.canonicalZoom,
            )
            revealDormantWatchtowers(
                revealedTiles = revealedTiles,
                discoveredAt = now,
                canonicalZoom = config.canonicalZoom,
            )

            Output.Success(claimedState)
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
