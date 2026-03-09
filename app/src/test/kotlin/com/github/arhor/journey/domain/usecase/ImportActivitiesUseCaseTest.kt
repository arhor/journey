package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.ActivityLogEntry
import com.github.arhor.journey.domain.model.ActivitySource
import com.github.arhor.journey.domain.model.ActivityType
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroStats
import com.github.arhor.journey.domain.model.ImportedActivityMetadata
import com.github.arhor.journey.domain.model.Progression
import com.github.arhor.journey.domain.model.RecordedActivity
import com.github.arhor.journey.domain.model.Reward
import com.github.arhor.journey.domain.progression.ActivityRewardCalculator
import com.github.arhor.journey.domain.progression.ProgressionEngine
import com.github.arhor.journey.domain.progression.ProgressionPolicy
import com.github.arhor.journey.domain.repository.ActivityLogInsertResult
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

class ImportActivitiesUseCaseTest {

    @Test
    fun `invoke should process imported records in batch transactions and apply rewards idempotently`() = runTest {
        // Given
        val initialHero = createHero(xpInLevel = 980L)
        val heroRepo = FakeHeroRepository(initialHero)
        val activityRepo = FakeActivityLogRepository(duplicateImportIds = setOf("record-2"))
        val tx = FakeTransactionRunner()
        val useCase = ImportActivitiesUseCase(
            heroRepository = heroRepo,
            activityLogRepository = activityRepo,
            transactionRunner = tx,
            rewardCalculator = ActivityRewardCalculator(),
            progressionEngine = ProgressionEngine(ProgressionPolicy()),
            clock = Clock.fixed(Instant.parse("2026-01-01T00:10:00Z"), ZoneOffset.UTC),
        )
        val records = listOf(
            createImportedRecordedActivity("record-1"),
            createImportedRecordedActivity("record-2"),
            createImportedRecordedActivity("record-3"),
        )

        // When
        val result = useCase(records = records, batchSize = 2)

        // Then
        tx.calls shouldBe 2
        result.importedCount shouldBe 3
        result.rewardedCount shouldBe 2
        result.skippedRewardCount shouldBe 1
        result.totalReward.xp shouldBe 40L
        result.totalReward.energyDelta shouldBe 4
        result.totalLevelUps shouldBe 1
        result.heroAfter.progression.level shouldBe 2
        heroRepo.current.value shouldBe result.heroAfter
        activityRepo.entries shouldHaveSize 3
    }

    @Test
    fun `invoke should skip reward application when imported record has no idempotent metadata`() = runTest {
        // Given
        val initialHero = createHero()
        val heroRepo = FakeHeroRepository(initialHero)
        val activityRepo = FakeActivityLogRepository()
        val useCase = ImportActivitiesUseCase(
            heroRepository = heroRepo,
            activityLogRepository = activityRepo,
            transactionRunner = FakeTransactionRunner(),
            rewardCalculator = ActivityRewardCalculator(),
            progressionEngine = ProgressionEngine(ProgressionPolicy()),
            clock = Clock.fixed(Instant.parse("2026-01-01T00:10:00Z"), ZoneOffset.UTC),
        )

        // When
        val result = useCase(records = listOf(createImportedRecordedActivity(importId = null)))

        // Then
        result.rewardedCount shouldBe 0
        result.skippedRewardCount shouldBe 1
        result.totalReward.xp shouldBe 0L
        result.totalReward.energyDelta shouldBe 0
        result.heroAfter shouldBe initialHero
        activityRepo.entries.single().reward.xp shouldBe 0L
        activityRepo.entries.single().reward.energyDelta shouldBe 0
    }



    @Test
    fun `invoke should avoid duplicate rewards when the same imported records are synced repeatedly`() = runTest {
        // Given
        val initialHero = createHero(xpInLevel = 100L)
        val heroRepo = FakeHeroRepository(initialHero)
        val activityRepo = FakeActivityLogRepository()
        val useCase = ImportActivitiesUseCase(
            heroRepository = heroRepo,
            activityLogRepository = activityRepo,
            transactionRunner = FakeTransactionRunner(),
            rewardCalculator = ActivityRewardCalculator(),
            progressionEngine = ProgressionEngine(ProgressionPolicy()),
            clock = Clock.fixed(Instant.parse("2026-01-01T00:10:00Z"), ZoneOffset.UTC),
        )
        val records = listOf(
            createImportedRecordedActivity("record-1"),
            createImportedRecordedActivity("record-2"),
        )

        // When
        val first = useCase(records = records)
        val second = useCase(records = records)

        // Then
        first.rewardedCount shouldBe 2
        first.totalReward.xp shouldBe 40L
        first.totalReward.energyDelta shouldBe 4
        second.rewardedCount shouldBe 0
        second.skippedRewardCount shouldBe 2
        second.totalReward.xp shouldBe 0L
        second.totalReward.energyDelta shouldBe 0
        second.heroAfter shouldBe first.heroAfter
    }

    private fun createHero(xpInLevel: Long = 10L) = Hero(
        id = "player",
        name = "Adventurer",
        stats = HeroStats(strength = 1, vitality = 1, dexterity = 1, stamina = 1),
        progression = Progression(level = 1, xpInLevel = xpInLevel),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private fun createImportedRecordedActivity(importId: String?) = RecordedActivity(
        type = ActivityType.WALK,
        source = ActivitySource.IMPORTED,
        startedAt = Instant.parse("2026-01-01T00:08:00Z"),
        duration = Duration.ofMinutes(2),
        distanceMeters = 200,
        steps = 1000,
        note = "Imported walk",
        importMetadata = importId?.let {
            ImportedActivityMetadata(
                externalRecordId = it,
                originPackageName = "com.google.android.apps.healthdata",
                timeBoundsHash = "hash-$it",
            )
        },
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
        private val duplicateImportIds: Set<String> = emptySet(),
    ) : ActivityLogRepository {
        val entries = mutableListOf<ActivityLogEntry>()
        private var nextId = 1L
        private val seenImportIds = mutableSetOf<String>()

        override fun observeHistory(): Flow<List<ActivityLogEntry>> = flowOf(entries.toList())

        override suspend fun insert(
            recorded: RecordedActivity,
            reward: Reward,
        ): ActivityLogInsertResult {
            val id = nextId++
            entries += ActivityLogEntry(id = id, recorded = recorded, reward = reward)

            val importId = recorded.importMetadata?.externalRecordId
            val isDuplicateFromSeed = importId != null && importId in duplicateImportIds
            val isDuplicateFromPreviousInsert = importId != null && !seenImportIds.add(importId)
            val shouldApplyReward = !isDuplicateFromSeed && !isDuplicateFromPreviousInsert

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
