package com.github.arhor.journey.ui.views.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.github.arhor.journey.core.logging.LoggerFactory
import com.github.arhor.journey.domain.model.ActivityLogEntry
import com.github.arhor.journey.domain.model.ActivitySource
import com.github.arhor.journey.domain.model.ActivityType
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.RecordedActivity
import com.github.arhor.journey.domain.model.Resource
import com.github.arhor.journey.domain.model.asResourceFlow
import com.github.arhor.journey.domain.progression.ProgressionPolicy
import com.github.arhor.journey.domain.usecase.LogActivityUseCase
import com.github.arhor.journey.domain.usecase.ObserveActivityLogUseCase
import com.github.arhor.journey.domain.usecase.ObserveCurrentHeroUseCase
import com.github.arhor.journey.ui.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import javax.inject.Inject

@Immutable
private data class State(
    val selectedActivityType: ActivityType = ActivityType.WALK,
    val durationMinutesInput: String = "30",
    val isSubmitting: Boolean = false,
    val refreshToken: Int = 0,
)

@Stable
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observeCurrentHero: ObserveCurrentHeroUseCase,
    private val observeActivityLog: ObserveActivityLogUseCase,
    private val logActivity: LogActivityUseCase,
    private val progressionPolicy: ProgressionPolicy,
    private val clock: Clock,
    loggerFactory: LoggerFactory,
) : MviViewModel<HomeUiState, HomeEffect, HomeIntent>(
    loggerFactory = loggerFactory,
    initialState = HomeUiState.Loading,
) {
    private val _state = MutableStateFlow(State())

    override fun buildUiState(): Flow<HomeUiState> =
        combine(
            _state,
            heroResourceFlow(),
            observeActivityLog(),
            ::intoUiState,
        ).distinctUntilChanged()

    override suspend fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.RetryLoad -> retryLoad()
            is HomeIntent.SelectActivityType -> _state.update {
                it.copy(selectedActivityType = intent.type)
            }

            is HomeIntent.ChangeDurationMinutes -> _state.update {
                it.copy(durationMinutesInput = intent.value)
            }

            HomeIntent.SubmitActivity -> submitActivity()
        }
    }

    private fun retryLoad() {
        _state.update {
            it.copy(refreshToken = it.refreshToken + 1)
        }
    }

    private suspend fun submitActivity() {
        val state = _state.value
        if (state.isSubmitting) {
            return
        }

        val minutes = state.durationMinutesOrNull()
        if (minutes == null) {
            emitEffect(HomeEffect.Error(INVALID_DURATION_MESSAGE))
            return
        }

        _state.update {
            it.copy(isSubmitting = true)
        }

        try {
            val result = logActivity(
                recorded = state.toRecordedActivity(
                    minutes = minutes,
                    clock = clock,
                ),
            )
            emitEffect(
                HomeEffect.Success(
                    message = if (result.levelUps > 0) {
                        "Activity logged: +${result.reward.xp} XP, +${result.levelUps} level(s)."
                    } else {
                        "Activity logged: +${result.reward.xp} XP."
                    },
                ),
            )
        } catch (e: Throwable) {
            emitEffect(
                HomeEffect.Error(
                    message = e.message ?: ACTIVITY_LOGGING_FAILED_MESSAGE,
                ),
            )
        } finally {
            _state.update {
                it.copy(isSubmitting = false)
            }
        }
    }

    private fun heroResourceFlow(): Flow<Resource<Hero>> =
        _state
            .map { it.refreshToken }
            .distinctUntilChanged()
            .flatMapLatest { observeCurrentHero().asResourceFlow() }

    private fun intoUiState(
        state: State,
        hero: Resource<Hero>,
        activityLog: List<ActivityLogEntry>,
    ): HomeUiState {
        return when (hero) {
            is Resource.Loading -> HomeUiState.Loading

            is Resource.Failure -> HomeUiState.Failure(
                errorMessage = hero.message ?: HERO_LOADING_FAILED_MESSAGE,
            )

            is Resource.Success -> {
                val importedTodayEntries = importedTodayEntries(activityLog)
                HomeUiState.Content(
                    heroName = hero.value.name,
                    level = hero.value.progression.level,
                    xpInLevel = hero.value.progression.xpInLevel,
                    xpToNextLevel = progressionPolicy.xpToNextLevel(hero.value.progression.level),
                    strength = hero.value.stats.strength,
                    vitality = hero.value.stats.vitality,
                    dexterity = hero.value.stats.dexterity,
                    stamina = hero.value.stats.stamina,
                    selectedActivityType = state.selectedActivityType,
                    durationMinutesInput = state.durationMinutesInput,
                    isSubmitting = state.isSubmitting,
                    importedTodayActivities = importedTodayEntries.size,
                    importedTodaySteps = importedTodayEntries.sumOf { it.recorded.steps?.toLong() ?: 0L },
                )
            }
        }
    }

    private fun importedTodayEntries(activityLog: List<ActivityLogEntry>): List<ActivityLogEntry> {
        val today = clock.instant().atZone(ZoneOffset.UTC).toLocalDate()
        return activityLog.filter { entry ->
            entry.recorded.source == ActivitySource.IMPORTED &&
                entry.recorded.startedAt.atZone(ZoneOffset.UTC).toLocalDate() == today
        }
    }

    private fun State.durationMinutesOrNull(): Int? =
        durationMinutesInput.toIntOrNull()?.takeIf { it in VALID_DURATION_RANGE_MINUTES }

    private fun State.toRecordedActivity(
        minutes: Int,
        clock: Clock,
    ): RecordedActivity {
        val duration = Duration.ofMinutes(minutes.toLong())
        val now = clock.instant()
        return RecordedActivity(
            type = selectedActivityType,
            source = ActivitySource.MANUAL,
            startedAt = now.minus(duration),
            duration = duration,
            distanceMeters = null,
            steps = null,
            note = null,
        )
    }

    private companion object {
        val VALID_DURATION_RANGE_MINUTES = 1..1440
        const val HERO_LOADING_FAILED_MESSAGE = "Failed to load hero state."
        const val INVALID_DURATION_MESSAGE = "Duration must be between 1 and 1440 minutes."
        const val ACTIVITY_LOGGING_FAILED_MESSAGE = "Failed to log activity."
    }
}
