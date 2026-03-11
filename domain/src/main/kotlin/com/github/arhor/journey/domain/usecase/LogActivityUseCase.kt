package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.activity.model.LogActivityResult
import com.github.arhor.journey.domain.activity.model.RecordedActivity
import com.github.arhor.journey.domain.model.Reward
import com.github.arhor.journey.domain.progression.ActivityRewardCalculator
import com.github.arhor.journey.domain.progression.ProgressionEngine
import com.github.arhor.journey.domain.repository.ActivityLogRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import com.github.arhor.journey.domain.repository.TransactionRunner
import java.time.Clock
import javax.inject.Inject

/**
 * Logs a new activity and applies its reward to the current hero atomically.
 *
 * MVI consumption sketch:
 * - inject [LogActivityUseCase] into a `@HiltViewModel`
 * - in `handleIntent`, call `logActivityUseCase(recordedActivity)`
 * - update state with the returned [heroAfter] and use [reward]/[levelUps] for transient UI feedback
 */
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
