package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.TransactionRunner
import com.github.arhor.journey.domain.internal.ActivityRewardCalculator
import com.github.arhor.journey.domain.internal.ProgressionEngine
import com.github.arhor.journey.domain.internal.ProgressionPolicy
import com.github.arhor.journey.domain.model.ActivityLogEntry
import com.github.arhor.journey.domain.model.ActivityLogInsertResult
import com.github.arhor.journey.domain.model.ActivitySource
import com.github.arhor.journey.domain.model.ActivityType
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroStats
import com.github.arhor.journey.domain.model.Progression
import com.github.arhor.journey.domain.model.RecordedActivity
import com.github.arhor.journey.domain.model.Reward
import com.github.arhor.journey.domain.repository.ActivityLogRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class LogActivityUseCaseTest {

    @Test
    fun `invoke should apply calculated reward and persist updated hero when log entry should apply reward`() = runTest {
        // Given
        val now = Instant.parse("2026-02-15T12:00:00Z")
        val heroBefore = hero(level = 1, xpInLevel = 900L)
        val heroRepository = FakeHeroRepository(hero = heroBefore)
        val activityLogRepository = FakeActivityLogRepository(
            insertResult = ActivityLogInsertResult(logEntryId = 77L, shouldApplyReward = true),
        )
        val transactionRunner = FakeTransactionRunner()
        val subject = LogActivityUseCase(
            heroRepository = heroRepository,
            activityLogRepository = activityLogRepository,
            transactionRunner = transactionRunner,
            rewardCalculator = ActivityRewardCalculator(),
            progressionEngine = ProgressionEngine(policy = ProgressionPolicy()),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        val recorded = recordedActivity(type = ActivityType.WALK, durationSeconds = 600, steps = 1_000)

        // When
        val result = subject(recorded)

        // Then
        transactionRunner.runCount shouldBe 1
        activityLogRepository.insertedRecorded shouldBe recorded
        activityLogRepository.insertedReward shouldBe Reward(xp = 100L, energyDelta = 2)
        result.logEntryId shouldBe 77L
        result.reward shouldBe Reward(xp = 100L, energyDelta = 2)
        result.heroBefore shouldBe heroBefore
        result.heroAfter.progression shouldBe Progression(level = 2, xpInLevel = 0L)
        result.heroAfter.stats shouldBe HeroStats(strength = 6, vitality = 6, dexterity = 6, stamina = 6)
        result.heroAfter.updatedAt shouldBe now
        result.levelUps shouldBe 1
        heroRepository.upserts shouldBe listOf(result.heroAfter)
    }

    @Test
    fun `invoke should skip progression and return zero reward when log entry should not apply reward`() = runTest {
        // Given
        val now = Instant.parse("2026-02-15T12:00:00Z")
        val heroBefore = hero(level = 3, xpInLevel = 250L)
        val heroRepository = FakeHeroRepository(hero = heroBefore)
        val activityLogRepository = FakeActivityLogRepository(
            insertResult = ActivityLogInsertResult(logEntryId = 91L, shouldApplyReward = false),
        )
        val subject = LogActivityUseCase(
            heroRepository = heroRepository,
            activityLogRepository = activityLogRepository,
            transactionRunner = FakeTransactionRunner(),
            rewardCalculator = ActivityRewardCalculator(),
            progressionEngine = ProgressionEngine(policy = ProgressionPolicy()),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        val recorded = recordedActivity(type = ActivityType.RUN, durationSeconds = 240, steps = 3_000)

        // When
        val result = subject(recorded)

        // Then
        result.logEntryId shouldBe 91L
        result.reward shouldBe Reward(xp = 0L, energyDelta = 0)
        result.heroBefore shouldBe heroBefore
        result.heroAfter shouldBe heroBefore
        result.levelUps shouldBe 0
        heroRepository.upserts shouldBe emptyList()
        activityLogRepository.insertedReward shouldBe Reward(xp = 60L, energyDelta = 6)
    }

    private fun hero(level: Int, xpInLevel: Long): Hero {
        val timestamp = Instant.parse("2026-01-01T00:00:00Z")

        return Hero(
            id = "hero-1",
            name = "Hero",
            stats = HeroStats(strength = 5, vitality = 5, dexterity = 5, stamina = 5),
            progression = Progression(level = level, xpInLevel = xpInLevel),
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }

    private fun recordedActivity(
        type: ActivityType,
        durationSeconds: Long,
        steps: Int?,
    ): RecordedActivity = RecordedActivity(
        type = type,
        source = ActivitySource.MANUAL,
        startedAt = Instant.parse("2026-02-14T10:00:00Z"),
        duration = Duration.ofSeconds(durationSeconds),
        distanceMeters = null,
        steps = steps,
        note = null,
    )

    private class FakeTransactionRunner : TransactionRunner {
        var runCount: Int = 0

        override suspend fun <T> runInTransaction(block: suspend () -> T): T {
            runCount += 1
            return block()
        }
    }

    private class FakeHeroRepository(
        private var hero: Hero,
    ) : HeroRepository {
        val upserts: MutableList<Hero> = mutableListOf()

        override fun observeCurrentHero(): Flow<Hero> = emptyFlow()

        override suspend fun getCurrentHero(): Hero = hero

        override suspend fun upsert(hero: Hero) {
            upserts += hero
            this.hero = hero
        }
    }

    private class FakeActivityLogRepository(
        private val insertResult: ActivityLogInsertResult,
    ) : ActivityLogRepository {
        var insertedRecorded: RecordedActivity? = null
        var insertedReward: Reward? = null

        override fun observeHistory(): Flow<List<ActivityLogEntry>> = emptyFlow()

        override suspend fun insert(recorded: RecordedActivity, reward: Reward): ActivityLogInsertResult {
            insertedRecorded = recorded
            insertedReward = reward
            return insertResult
        }
    }
}
