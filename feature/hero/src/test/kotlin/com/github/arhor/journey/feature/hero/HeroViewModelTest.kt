package com.github.arhor.journey.feature.hero

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
import com.github.arhor.journey.domain.usecase.LogActivityUseCase
import com.github.arhor.journey.domain.usecase.ObserveActivityLogUseCase
import com.github.arhor.journey.domain.usecase.ObserveHeroUseCase
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class HeroViewModelTest {

    private val fixedNow = Instant.parse("2026-03-15T10:15:30Z")
    private val fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    @Test
    fun `uiState should expose derived content when hero and activity history are available`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val heroRepository = FakeHeroRepository(
            initialHero = defaultHero().copy(
                progression = Progression(level = 3, xpInLevel = 450L),
            ),
        )
        val activityLogRepository = FakeActivityLogRepository(
            initialHistory = listOf(
                importedLogEntry(id = 1L, startedAt = fixedNow.minusSeconds(60L), steps = 1300),
                importedLogEntry(id = 2L, startedAt = fixedNow.minusSeconds(120L), steps = null),
                importedLogEntry(id = 3L, startedAt = fixedNow.minus(Duration.ofDays(1)).minusSeconds(30L), steps = 999),
                manualLogEntry(id = 4L, startedAt = fixedNow.minusSeconds(30L), steps = 3000),
            ),
        )
        val viewModel = createSubject(
            heroRepository = heroRepository,
            activityLogRepository = activityLogRepository,
        )
        try {
            // When
            val uiState = viewModel.awaitContent()

            // Then
            uiState.heroName shouldBe "Ayla"
            uiState.level shouldBe 3
            uiState.xpInLevel shouldBe 450L
            uiState.xpToNextLevel shouldBe 3000L
            uiState.importedTodayActivities shouldBe 2
            uiState.importedTodaySteps shouldBe 1300L
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should emit error and skip logging when duration is outside valid range`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val activityLogRepository = FakeActivityLogRepository()
        val viewModel = createSubject(activityLogRepository = activityLogRepository)
        try {
            viewModel.awaitContent()
            val effectDeferred = async { viewModel.effects.first() }
            runCurrent()

            // When
            viewModel.dispatch(HeroIntent.ChangeDurationMinutes("0"))
            viewModel.dispatch(HeroIntent.SubmitActivity)
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe HeroEffect.Error("Duration must be between 1 and 1440 minutes.")
            activityLogRepository.insertCalls shouldBe 0
            viewModel.awaitContent { it.durationMinutesInput == "0" }.isSubmitting shouldBe false
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should log activity and emit level-up success message when valid input is submitted`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val heroRepository = FakeHeroRepository(
            initialHero = defaultHero().copy(
                progression = Progression(level = 1, xpInLevel = 900L),
            ),
        )
        val activityLogRepository = FakeActivityLogRepository()
        val viewModel = createSubject(
            heroRepository = heroRepository,
            activityLogRepository = activityLogRepository,
        )
        try {
            viewModel.awaitContent()
            val effectDeferred = async { viewModel.effects.first() }
            runCurrent()

            // When
            viewModel.dispatch(HeroIntent.SubmitActivity)
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe HeroEffect.Success("Activity logged: +300 XP, +1 level(s).")
            activityLogRepository.insertCalls shouldBe 1
            activityLogRepository.recordedActivities.single() shouldBe RecordedActivity(
                type = ActivityType.WALK,
                source = ActivitySource.MANUAL,
                startedAt = fixedNow.minus(Duration.ofMinutes(30L)),
                duration = Duration.ofMinutes(30L),
                distanceMeters = null,
                steps = null,
                note = null,
                importMetadata = null,
            )
            heroRepository.currentHero.progression.level shouldBe 2
            heroRepository.currentHero.progression.xpInLevel shouldBe 200L
            viewModel.awaitContent {
                it.level == 2 && it.xpInLevel == 200L
            }.isSubmitting shouldBe false
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should emit error and reset submitting flag when activity logging fails`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val activityLogRepository = FakeActivityLogRepository(
            onInsert = { _, _ ->
                throw IllegalStateException("Storage unavailable.")
            },
        )
        val viewModel = createSubject(activityLogRepository = activityLogRepository)
        try {
            viewModel.awaitContent()
            val effectDeferred = async { viewModel.effects.first() }
            runCurrent()

            // When
            viewModel.dispatch(HeroIntent.SubmitActivity)
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe HeroEffect.Error("Storage unavailable.")
            activityLogRepository.insertCalls shouldBe 1
            viewModel.awaitContent().isSubmitting shouldBe false
        } finally {
            tearDownMainDispatcher()
        }
    }

    @Test
    fun `dispatch should update selected activity and duration when corresponding intents are received`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val viewModel = createSubject()
        try {
            viewModel.awaitContent()

            // When
            viewModel.dispatch(HeroIntent.SelectActivityType(ActivityType.WORKOUT))
            viewModel.dispatch(HeroIntent.ChangeDurationMinutes("45"))
            val content = viewModel.awaitContent {
                it.selectedActivityType == ActivityType.WORKOUT &&
                    it.durationMinutesInput == "45"
            }

            // Then
            content.selectedActivityType shouldBe ActivityType.WORKOUT
            content.durationMinutesInput shouldBe "45"
        } finally {
            tearDownMainDispatcher()
        }
    }

    private suspend fun HeroViewModel.awaitContent(
        predicate: (HeroUiState.Content) -> Boolean = { true },
    ): HeroUiState.Content = uiState
        .mapNotNull { it as? HeroUiState.Content }
        .first(predicate)

    private fun TestScope.tearDownMainDispatcher() {
        advanceUntilIdle()
        advanceTimeBy(5_001L)
        runCurrent()
        Dispatchers.resetMain()
    }

    private fun createSubject(
        heroRepository: HeroRepository = FakeHeroRepository(initialHero = defaultHero()),
        activityLogRepository: FakeActivityLogRepository = FakeActivityLogRepository(),
    ): HeroViewModel {
        val logActivity = LogActivityUseCase(
            heroRepository = heroRepository,
            activityLogRepository = activityLogRepository,
            transactionRunner = ImmediateTransactionRunner,
            rewardCalculator = ActivityRewardCalculator(),
            progressionEngine = ProgressionEngine(policy = ProgressionPolicy()),
            clock = fixedClock,
        )

        return HeroViewModel(
            observeCurrentHero = ObserveHeroUseCase(heroRepository),
            observeActivityLog = ObserveActivityLogUseCase(activityLogRepository),
            logActivity = logActivity,
            progressionPolicy = ProgressionPolicy(),
            clock = fixedClock,
        )
    }

    private fun defaultHero() = Hero(
        id = "hero-1",
        name = "Ayla",
        stats = HeroStats(
            strength = 10,
            vitality = 8,
            dexterity = 7,
            stamina = 9,
        ),
        progression = Progression(level = 1, xpInLevel = 0L),
        createdAt = fixedNow.minus(Duration.ofDays(7)),
        updatedAt = fixedNow.minus(Duration.ofDays(1)),
    )

    private fun importedLogEntry(
        id: Long,
        startedAt: Instant,
        steps: Int?,
    ) = ActivityLogEntry(
        id = id,
        recorded = RecordedActivity(
            type = ActivityType.WALK,
            source = ActivitySource.IMPORTED,
            startedAt = startedAt,
            duration = Duration.ofMinutes(10L),
            distanceMeters = null,
            steps = steps,
            note = null,
        ),
        reward = Reward(xp = 0L),
    )

    private fun manualLogEntry(
        id: Long,
        startedAt: Instant,
        steps: Int?,
    ) = ActivityLogEntry(
        id = id,
        recorded = RecordedActivity(
            type = ActivityType.RUN,
            source = ActivitySource.MANUAL,
            startedAt = startedAt,
            duration = Duration.ofMinutes(5L),
            distanceMeters = null,
            steps = steps,
            note = null,
        ),
        reward = Reward(xp = 0L),
    )

    private class FakeHeroRepository(
        initialHero: Hero,
    ) : HeroRepository {
        private val hero = MutableStateFlow(initialHero)

        val currentHero: Hero
            get() = hero.value

        override fun observeCurrentHero(): Flow<Hero> = hero

        override suspend fun getCurrentHero(): Hero = hero.value

        override suspend fun upsert(hero: Hero) {
            this.hero.value = hero
        }
    }

    private class FakeActivityLogRepository(
        initialHistory: List<ActivityLogEntry> = emptyList(),
        private val onInsert: suspend (RecordedActivity, Reward) -> ActivityLogInsertResult =
            { _, _ -> ActivityLogInsertResult(logEntryId = 1L, shouldApplyReward = true) },
    ) : ActivityLogRepository {
        private val history = MutableStateFlow(initialHistory)

        var insertCalls: Int = 0
            private set

        val recordedActivities: MutableList<RecordedActivity> = mutableListOf()

        override fun observeHistory(): Flow<List<ActivityLogEntry>> = history

        override suspend fun insert(
            recorded: RecordedActivity,
            reward: Reward,
        ): ActivityLogInsertResult {
            insertCalls += 1
            recordedActivities += recorded
            val result = onInsert(recorded, reward)
            history.update { current ->
                listOf(
                    ActivityLogEntry(
                        id = result.logEntryId,
                        recorded = recorded,
                        reward = reward,
                    )
                ) + current
            }
            return result
        }
    }

    private data object ImmediateTransactionRunner : TransactionRunner {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
}
