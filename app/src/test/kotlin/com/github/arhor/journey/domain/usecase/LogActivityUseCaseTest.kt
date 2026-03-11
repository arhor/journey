package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.activity.model.ActivityLogEntry
import com.github.arhor.journey.domain.activity.model.ActivitySource
import com.github.arhor.journey.domain.activity.model.ActivityType
import com.github.arhor.journey.domain.player.model.Hero
import com.github.arhor.journey.domain.player.model.HeroStats
import com.github.arhor.journey.domain.model.Progression
import com.github.arhor.journey.domain.activity.model.RecordedActivity
import com.github.arhor.journey.domain.model.Reward
import com.github.arhor.journey.domain.progression.ActivityRewardCalculator
import com.github.arhor.journey.domain.progression.ProgressionEngine
import com.github.arhor.journey.domain.progression.ProgressionPolicy
import com.github.arhor.journey.domain.activity.model.ActivityLogInsertResult
import com.github.arhor.journey.domain.repository.ActivityLogRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import com.github.arhor.journey.domain.repository.TransactionRunner
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class LogActivityUseCaseTest {

    @Test
    fun `invoke should persist activity log and update hero when activity is logged`() = runTest {
        // Given
        val initialHero = createHero()
        val heroRepo = FakeHeroRepository(initialHero)
        val activityRepo = FakeActivityLogRepository()
        val tx = FakeTransactionRunner()

        val clock = Clock.fixed(Instant.parse("2026-01-01T00:10:00Z"), ZoneOffset.UTC)
        val useCase = LogActivityUseCase(
            heroRepository = heroRepo,
            activityLogRepository = activityRepo,
            transactionRunner = tx,
            rewardCalculator = ActivityRewardCalculator(),
            progressionEngine = ProgressionEngine(ProgressionPolicy()),
            clock = clock,
        )

        val recorded = createRecordedActivity()

        // When
        val result = useCase(recorded)

        // Then
        tx.calls shouldBe 1
        result.reward.xp shouldBe 20L
        result.reward.energyDelta shouldBe 2
        result.levelUps shouldBe 1
        result.heroAfter.progression.level shouldBe 2
        result.heroAfter.progression.xpInLevel shouldBe 10L

        heroRepo.current.value shouldBe result.heroAfter
        activityRepo.entries shouldHaveSize 1
        activityRepo.entries.single().reward shouldBe Reward(xp = 20L, energyDelta = 2)
    }

    @Test
    fun `invoke should skip reward application when repository reports duplicate import`() = runTest {
        // Given
        val initialHero = createHero()
        val heroRepo = FakeHeroRepository(initialHero)
        val activityRepo = FakeActivityLogRepository(shouldApplyReward = false)
        val tx = FakeTransactionRunner()

        val useCase = LogActivityUseCase(
            heroRepository = heroRepo,
            activityLogRepository = activityRepo,
            transactionRunner = tx,
            rewardCalculator = ActivityRewardCalculator(),
            progressionEngine = ProgressionEngine(ProgressionPolicy()),
            clock = Clock.fixed(Instant.parse("2026-01-01T00:10:00Z"), ZoneOffset.UTC),
        )

        // When
        val result = useCase(createRecordedActivity())

        // Then
        result.reward.xp shouldBe 0L
        result.reward.energyDelta shouldBe 0
        result.levelUps shouldBe 0
        result.heroAfter shouldBe initialHero
        heroRepo.current.value shouldBe initialHero
    }

    private fun createHero() = Hero(
        id = "player",
        name = "Adventurer",
        stats = HeroStats(strength = 1, vitality = 1, dexterity = 1, stamina = 1),
        progression = Progression(level = 1, xpInLevel = 990L),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private fun createRecordedActivity() = RecordedActivity(
        type = ActivityType.WALK,
        source = ActivitySource.MANUAL,
        startedAt = Instant.parse("2026-01-01T00:08:00Z"),
        duration = Duration.ofMinutes(2),
        distanceMeters = 200,
        steps = 1000,
        note = "Evening walk",
    )

    private class FakeHeroRepository(initial: Hero) : HeroRepository {
        val current = MutableStateFlow(initial)

        override fun observeCurrentHero(): Flow<Hero> = current

        override suspend fun getCurrentHero(): Hero = current.value

        override suspend fun upsert(hero: Hero) {
            current.value = hero
        }
    }

    private class FakeActivityLogRepository(
        private val shouldApplyReward: Boolean = true,
    ) : ActivityLogRepository {
        val entries = mutableListOf<ActivityLogEntry>()
        private var nextId = 1L

        override fun observeHistory(): Flow<List<ActivityLogEntry>> = flowOf(entries.toList())

        override suspend fun insert(
            recorded: RecordedActivity,
            reward: Reward,
        ): ActivityLogInsertResult {
            val id = nextId++
            entries += ActivityLogEntry(id = id, recorded = recorded, reward = reward)
            return ActivityLogInsertResult(logEntryId = id, shouldApplyReward = shouldApplyReward)
        }
    }

    private class FakeTransactionRunner : TransactionRunner {
        var calls: Int = 0

        override suspend fun <T> runInTransaction(block: suspend () -> T): T {
            calls += 1
            return block()
        }
    }
}
