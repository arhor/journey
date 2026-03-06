package com.github.arhor.journey.ui.views.home

import com.github.arhor.journey.core.logging.NoOpLoggerFactory
import com.github.arhor.journey.domain.model.ActivitySource
import com.github.arhor.journey.domain.model.ActivityType
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroStats
import com.github.arhor.journey.domain.model.Progression
import com.github.arhor.journey.domain.model.Reward
import com.github.arhor.journey.domain.progression.ProgressionPolicy
import com.github.arhor.journey.domain.usecase.LogActivityResult
import com.github.arhor.journey.domain.usecase.LogActivityUseCase
import com.github.arhor.journey.domain.usecase.ObserveActivityLogUseCase
import com.github.arhor.journey.domain.usecase.ObserveCurrentHeroUseCase
import com.github.arhor.journey.test.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initialize should expose hero content when observeCurrentHero emits`() = runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val hero = hero()
        val heroFlow = MutableSharedFlow<Hero>(replay = 1).apply { tryEmit(hero) }
        val observeCurrentHeroUseCase = mockk<ObserveCurrentHeroUseCase>()
        val observeActivityLogUseCase = mockk<ObserveActivityLogUseCase>()
        val logActivityUseCase = mockk<LogActivityUseCase>()
        val clock = Clock.fixed(Instant.parse("2026-01-01T01:00:00Z"), ZoneOffset.UTC)
        every { observeCurrentHeroUseCase.invoke() } returns heroFlow
        every { observeActivityLogUseCase.invoke() } returns flowOf(emptyList())

        val vm = HomeViewModel(
            observeCurrentHero = observeCurrentHeroUseCase,
            observeActivityLog = observeActivityLogUseCase,
            logActivity = logActivityUseCase,
            progressionPolicy = ProgressionPolicy(),
            clock = clock,
            loggerFactory = NoOpLoggerFactory,
        )
        backgroundScope.launch { vm.uiState.collect() }

        // When
        advanceUntilIdle()

        // Then
        val state = vm.uiState.first { it is HomeUiState.Content } as HomeUiState.Content
        state.heroName shouldBe "Adventurer"
        state.level shouldBe 2
        state.xpInLevel shouldBe 250L
        state.xpToNextLevel shouldBe 2000L
        state.strength shouldBe 5
        state.vitality shouldBe 4
        state.dexterity shouldBe 3
        state.stamina shouldBe 2
        state.selectedActivityType shouldBe ActivityType.WALK
        state.durationMinutesInput shouldBe "30"
        state.isSubmitting shouldBe false
        state.importedTodayActivities shouldBe 0
        state.importedTodaySteps shouldBe 0L
    }

    @Test
    fun `submit should call LogActivityUseCase when duration is valid`() = runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val hero = hero()
        val heroFlow = MutableSharedFlow<Hero>(replay = 1).apply { tryEmit(hero) }
        val observeCurrentHeroUseCase = mockk<ObserveCurrentHeroUseCase>()
        val observeActivityLogUseCase = mockk<ObserveActivityLogUseCase>()
        val logActivityUseCase = mockk<LogActivityUseCase>()
        val clock = Clock.fixed(Instant.parse("2026-01-01T01:00:00Z"), ZoneOffset.UTC)
        every { observeCurrentHeroUseCase.invoke() } returns heroFlow
        every { observeActivityLogUseCase.invoke() } returns flowOf(emptyList())
        coEvery { logActivityUseCase.invoke(any()) } returns logActivityResult(hero)

        val vm = HomeViewModel(
            observeCurrentHero = observeCurrentHeroUseCase,
            observeActivityLog = observeActivityLogUseCase,
            logActivity = logActivityUseCase,
            progressionPolicy = ProgressionPolicy(),
            clock = clock,
            loggerFactory = NoOpLoggerFactory,
        )
        backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        val effect = async { vm.effects.first() }

        // When
        vm.dispatch(HomeIntent.SelectActivityType(ActivityType.RUN))
        advanceUntilIdle()
        (vm.uiState.value as HomeUiState.Content).selectedActivityType shouldBe ActivityType.RUN

        vm.dispatch(HomeIntent.ChangeDurationMinutes("20"))
        advanceUntilIdle()
        (vm.uiState.value as HomeUiState.Content).durationMinutesInput shouldBe "20"

        vm.dispatch(HomeIntent.SubmitActivity)
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) {
            logActivityUseCase.invoke(
                match { recorded ->
                    recorded.type == ActivityType.RUN &&
                        recorded.source == ActivitySource.MANUAL &&
                        recorded.duration == Duration.ofMinutes(20) &&
                        recorded.startedAt == Instant.parse("2026-01-01T00:40:00Z") &&
                        recorded.distanceMeters == null &&
                        recorded.steps == null &&
                        recorded.note == null
                },
            )
        }
        effect.await() shouldBe HomeEffect.Success("Activity logged: +40 XP.")
        (vm.uiState.value as HomeUiState.Content).isSubmitting shouldBe false
    }

    @Test
    fun `submit should emit error and skip LogActivityUseCase when duration is invalid`() = runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val heroFlow = MutableSharedFlow<Hero>(replay = 1).apply { tryEmit(hero()) }
        val observeCurrentHeroUseCase = mockk<ObserveCurrentHeroUseCase>()
        val observeActivityLogUseCase = mockk<ObserveActivityLogUseCase>()
        val logActivityUseCase = mockk<LogActivityUseCase>()
        val clock = Clock.fixed(Instant.parse("2026-01-01T01:00:00Z"), ZoneOffset.UTC)
        every { observeCurrentHeroUseCase.invoke() } returns heroFlow
        every { observeActivityLogUseCase.invoke() } returns flowOf(emptyList())

        val vm = HomeViewModel(
            observeCurrentHero = observeCurrentHeroUseCase,
            observeActivityLog = observeActivityLogUseCase,
            logActivity = logActivityUseCase,
            progressionPolicy = ProgressionPolicy(),
            clock = clock,
            loggerFactory = NoOpLoggerFactory,
        )
        backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        val effect = async { vm.effects.first() }

        // When
        vm.dispatch(HomeIntent.ChangeDurationMinutes("0"))
        advanceUntilIdle()
        (vm.uiState.value as HomeUiState.Content).durationMinutesInput shouldBe "0"

        vm.dispatch(HomeIntent.SubmitActivity)
        advanceUntilIdle()

        // Then
        coVerify(exactly = 0) { logActivityUseCase.invoke(any()) }
        effect.await() shouldBe HomeEffect.Error("Duration must be between 1 and 1440 minutes.")
    }

    @Test
    fun `submit should emit failure effect when LogActivityUseCase throws`() = runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val heroFlow = MutableSharedFlow<Hero>(replay = 1).apply { tryEmit(hero()) }
        val observeCurrentHeroUseCase = mockk<ObserveCurrentHeroUseCase>()
        val observeActivityLogUseCase = mockk<ObserveActivityLogUseCase>()
        val logActivityUseCase = mockk<LogActivityUseCase>()
        val clock = Clock.fixed(Instant.parse("2026-01-01T01:00:00Z"), ZoneOffset.UTC)
        every { observeCurrentHeroUseCase.invoke() } returns heroFlow
        every { observeActivityLogUseCase.invoke() } returns flowOf(emptyList())
        coEvery { logActivityUseCase.invoke(any()) } throws IllegalStateException("boom")

        val vm = HomeViewModel(
            observeCurrentHero = observeCurrentHeroUseCase,
            observeActivityLog = observeActivityLogUseCase,
            logActivity = logActivityUseCase,
            progressionPolicy = ProgressionPolicy(),
            clock = clock,
            loggerFactory = NoOpLoggerFactory,
        )
        backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        val effect = async { vm.effects.first() }

        // When
        vm.dispatch(HomeIntent.SubmitActivity)
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { logActivityUseCase.invoke(any()) }
        effect.await() shouldBe HomeEffect.Error("boom")
    }

    private fun hero(): Hero =
        Hero(
            id = "player",
            name = "Adventurer",
            stats = HeroStats(
                strength = 5,
                vitality = 4,
                dexterity = 3,
                stamina = 2,
            ),
            progression = Progression(
                level = 2,
                xpInLevel = 250L,
            ),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

    private fun logActivityResult(hero: Hero): LogActivityResult =
        LogActivityResult(
            logEntryId = 1L,
            reward = Reward(xp = 40L),
            heroBefore = hero,
            heroAfter = hero,
            levelUps = 0,
        )
}
