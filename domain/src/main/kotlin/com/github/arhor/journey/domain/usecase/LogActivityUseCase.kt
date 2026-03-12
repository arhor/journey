package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.TransactionRunner
import com.github.arhor.journey.domain.internal.ActivityRewardCalculator
import com.github.arhor.journey.domain.internal.ProgressionEngine
import com.github.arhor.journey.domain.model.LogActivityResult
import com.github.arhor.journey.domain.model.RecordedActivity
import com.github.arhor.journey.domain.model.Reward
import com.github.arhor.journey.domain.repository.ActivityLogRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Logs a new activity and applies its reward to the current hero atomically.
 */
@Singleton
class LogActivityUseCase @Inject constructor(
    private val heroRepository: HeroRepository,
    private val activityLogRepository: ActivityLogRepository,
    private val transactionRunner: TransactionRunner,
    private val rewardCalculator: ActivityRewardCalculator,
    private val progressionEngine: ProgressionEngine,
    private val clock: Clock,
) {

    suspend operator fun invoke(recorded: RecordedActivity): LogActivityResult =
        transactionRunner.runInTransaction {
            val now = clock.instant()
            val heroBefore = heroRepository.getCurrentHero()
            val calculatedReward = rewardCalculator.calculate(recorded)

            val insertResult = activityLogRepository.insert(recorded = recorded, reward = calculatedReward)
            if (!insertResult.shouldApplyReward) {
                return@runInTransaction LogActivityResult(
                    logEntryId = insertResult.logEntryId,
                    reward = Reward(xp = 0L, energyDelta = 0),
                    heroBefore = heroBefore,
                    heroAfter = heroBefore,
                    levelUps = 0,
                )
            }

            val applied = progressionEngine.applyReward(hero = heroBefore, reward = calculatedReward, now = now)
            val heroAfter = applied.hero
            heroRepository.upsert(heroAfter)

            LogActivityResult(
                logEntryId = insertResult.logEntryId,
                reward = calculatedReward,
                heroBefore = heroBefore,
                heroAfter = heroAfter,
                levelUps = applied.levelUps,
            )
        }
}
