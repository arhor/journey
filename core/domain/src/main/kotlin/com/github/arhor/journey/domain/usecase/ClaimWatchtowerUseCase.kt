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
import kotlinx.coroutines.CancellationException
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
    ): Output<WatchtowerState, ClaimWatchtowerError> = try {
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
        val config = when (val result = getExplorationTileRuntimeConfig()) {
            is Output.Success -> result.value
            is Output.Failure -> {
                return Output.Failure(
                    ClaimWatchtowerError.Unexpected(
                        result.error.asThrowable("Failed to load exploration tile runtime config."),
                    ),
                )
            }
        }

        transactionRunner.runInTransaction {
            val freshRecord = watchtowerRepository.getById(id)
                ?: abortTransaction(ClaimWatchtowerError.NotFound(id))
            val freshState = freshRecord.state
                ?: abortTransaction(ClaimWatchtowerError.NotDiscovered(id))
            if (freshState.claimedAt != null) {
                abortTransaction(ClaimWatchtowerError.AlreadyClaimed(id))
            }

            val spent = heroInventoryRepository.spendAmount(
                heroId = hero.id,
                resourceTypeId = claimCost.resourceTypeId,
                amount = claimCost.amount,
                updatedAt = now,
            )
            if (spent == null) {
                abortTransaction(
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
                abortTransaction(ClaimWatchtowerError.AlreadyClaimed(id))
            }

            val claimedState = freshState.copy(
                claimedAt = now,
                level = 1,
                updatedAt = now,
            )
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
    } catch (exception: TransactionAbortException) {
        @Suppress("UNCHECKED_CAST")
        Output.Failure(exception.error as ClaimWatchtowerError)
    } catch (exception: Throwable) {
        if (exception is CancellationException) {
            throw exception
        }

        Output.Failure(ClaimWatchtowerError.Unexpected(exception))
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
