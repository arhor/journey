package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.ActivityLogEntry
import com.github.arhor.journey.domain.model.ActivitySource
import com.github.arhor.journey.domain.model.ActivityType
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroStats
import com.github.arhor.journey.domain.model.Progression
import com.github.arhor.journey.domain.model.RecordedActivity
import com.github.arhor.journey.domain.model.Reward
import com.github.arhor.journey.domain.progression.ActivityRewardCalculator
import com.github.arhor.journey.domain.progression.ProgressionEngine
import com.github.arhor.journey.domain.progression.ProgressionPolicy
import com.github.arhor.journey.domain.repository.ActivityLogRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import com.github.arhor.journey.domain.repository.TransactionRunner
import com.google.common.truth.Truth.assertThat
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
    fun `invoke persists log entry and updates hero in one transaction`() = runTest {
        val initialHero = Hero(
            id = "player",
            name = "Adventurer",
            stats = HeroStats(strength = 1, vitality = 1, dexterity = 1, stamina = 1),
            progression = Progression(level = 1, xpInLevel = 990L),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

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

        val recorded = RecordedActivity(
            type = ActivityType.WALK,
            source = ActivitySource.MANUAL,
            startedAt = Instant.parse("2026-01-01T00:08:00Z"),
            duration = Duration.ofMinutes(2),
            distanceMeters = 200,
            steps = 300,
            note = "Evening walk",
        )

        val result = useCase(recorded)

        assertThat(tx.calls).isEqualTo(1)
        assertThat(result.reward.xp).isEqualTo(20L)
        assertThat(result.levelUps).isEqualTo(1)
        assertThat(result.heroAfter.progression.level).isEqualTo(2)
        assertThat(result.heroAfter.progression.xpInLevel).isEqualTo(10L)

        assertThat(heroRepo.current.value).isEqualTo(result.heroAfter)
        assertThat(activityRepo.entries).hasSize(1)
        assertThat(activityRepo.entries.single().reward).isEqualTo(Reward(xp = 20L))
    }

    private class FakeHeroRepository(initial: Hero) : HeroRepository {
        val current = MutableStateFlow(initial)

        override fun observeCurrentHero(): Flow<Hero> = current

        override suspend fun getCurrentHero(): Hero = current.value

        override suspend fun upsert(hero: Hero) {
            current.value = hero
        }
    }

    private class FakeActivityLogRepository : ActivityLogRepository {
        val entries = mutableListOf<ActivityLogEntry>()
        private var nextId = 1L

        override fun observeHistory(): Flow<List<ActivityLogEntry>> = flowOf(entries.toList())

        override suspend fun insert(
            recorded: RecordedActivity,
            reward: Reward,
        ): Long {
            val id = nextId++
            entries += ActivityLogEntry(id = id, recorded = recorded, reward = reward)
            return id
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

