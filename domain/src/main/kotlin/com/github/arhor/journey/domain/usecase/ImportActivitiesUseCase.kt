package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.activity.model.ImportActivitiesResult
import com.github.arhor.journey.domain.activity.model.RecordedActivity
import com.github.arhor.journey.domain.model.Reward
import com.github.arhor.journey.domain.progression.ActivityRewardCalculator
import com.github.arhor.journey.domain.progression.ProgressionEngine
import com.github.arhor.journey.domain.repository.ActivityLogRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import com.github.arhor.journey.domain.repository.TransactionRunner
import java.time.Clock
import javax.inject.Inject

private const val DEFAULT_BATCH_SIZE = 100

/**
 * Imports external activity records in batches while reusing the same reward/progression logic
 * as manual activity logging.
 *
 * Reward application is restricted to records with idempotent import metadata.
 */
class ImportActivitiesUseCase @Inject constructor(
    private val heroRepository: HeroRepository,
    private val activityLogRepository: ActivityLogRepository,
    private val transactionRunner: TransactionRunner,
    private val rewardCalculator: ActivityRewardCalculator,
    private val progressionEngine: ProgressionEngine,
    private val clock: Clock,
) {

    suspend operator fun invoke(
        records: List<RecordedActivity>,
        batchSize: Int = DEFAULT_BATCH_SIZE,
    ): ImportActivitiesResult {
        require(batchSize > 0) { "batchSize must be greater than 0." }

        val heroBefore = heroRepository.getCurrentHero()
        if (records.isEmpty()) {
            return ImportActivitiesResult(
                heroBefore = heroBefore,
                heroAfter = heroBefore,
                importedCount = 0,
                rewardedCount = 0,
                skippedRewardCount = 0,
                totalReward = Reward(xp = 0L, energyDelta = 0),
                totalLevelUps = 0,
            )
        }

        var totalRewardXp = 0L
        var totalRewardEnergy = 0
        var totalLevelUps = 0
        var rewardedCount = 0
        var skippedRewardCount = 0

        records.chunked(batchSize).forEach { batch ->
            transactionRunner.runInTransaction {
                val now = clock.instant()
                var currentHero = heroRepository.getCurrentHero()

                batch.forEach { recorded ->
                    val reward = rewardCalculator.calculate(recorded)
                    val hasIdempotentImportKey = recorded.importMetadata != null
                    val persistedReward = if (hasIdempotentImportKey) reward else Reward(xp = 0L, energyDelta = 0)

                    val insertResult = activityLogRepository.insert(
                        recorded = recorded,
                        reward = persistedReward,
                    )

                    if (!hasIdempotentImportKey || !insertResult.shouldApplyReward) {
                        skippedRewardCount += 1
                        return@forEach
                    }

                    val applied = progressionEngine.applyReward(
                        hero = currentHero,
                        reward = reward,
                        now = now,
                    )
                    currentHero = applied.hero
                    rewardedCount += 1
                    totalRewardXp += reward.xp
                    totalRewardEnergy += reward.energyDelta
                    totalLevelUps += applied.levelUps
                }

                heroRepository.upsert(currentHero)
            }
        }

        val heroAfter = heroRepository.getCurrentHero()
        return ImportActivitiesResult(
            heroBefore = heroBefore,
            heroAfter = heroAfter,
            importedCount = records.size,
            rewardedCount = rewardedCount,
            skippedRewardCount = skippedRewardCount,
            totalReward = Reward(xp = totalRewardXp, energyDelta = totalRewardEnergy),
            totalLevelUps = totalLevelUps,
        )
    }
}
