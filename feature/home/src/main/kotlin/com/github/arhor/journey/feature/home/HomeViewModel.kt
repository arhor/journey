package com.github.arhor.journey.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.github.arhor.journey.domain.internal.ProgressionPolicy
import com.github.arhor.journey.domain.model.ActivityLogEntry
import com.github.arhor.journey.domain.model.ActivitySource
import com.github.arhor.journey.domain.model.ActivityType
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.RecordedActivity
import com.github.arhor.journey.domain.usecase.LogActivityUseCase
import com.github.arhor.journey.domain.usecase.ObserveActivityLogUseCase
import com.github.arhor.journey.domain.usecase.ObserveHeroUseCase
import com.github.arhor.journey.core.ui.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
)

@Stable
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observeCurrentHero: ObserveHeroUseCase,
    private val observeActivityLog: ObserveActivityLogUseCase,
    private val logActivity: LogActivityUseCase,
    private val progressionPolicy: ProgressionPolicy,
    private val clock: Clock,
) : MviViewModel<HomeUiState, HomeEffect, HomeIntent>(
    initialState = HomeUiState.Loading,
) {
    private val _state = MutableStateFlow(State())

    override fun buildUiState(): Flow<HomeUiState> =
        combine(
            _state,
            observeCurrentHero(),
            observeActivityLog(),
            ::intoUiState,
        ).catch {
            emit(
                HomeUiState.Failure(
                    errorMessage = it.message ?: HOME_LOADING_FAILED_MESSAGE,
                ),
            )
        }.distinctUntilChanged()

    override suspend fun handleIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.SelectActivityType -> onSelectActivityType(intent)
            is HomeIntent.ChangeDurationMinutes -> onChangeDurationMinutes(intent)
            is HomeIntent.SubmitActivity -> onSubmitActivity()
        }
    }

    /* ------------------------------------------ Internal implementation ------------------------------------------- */

    private fun intoUiState(
        state: State,
        hero: Hero,
        activityLog: List<ActivityLogEntry>,
    ): HomeUiState {
        val importedTodayEntries = importedTodayEntries(activityLog)

        return HomeUiState.Content(
            heroName = hero.name,
            level = hero.progression.level,
            xpInLevel = hero.progression.xpInLevel,
            xpToNextLevel = progressionPolicy.xpToNextLevel(hero.progression.level),
            strength = hero.stats.strength,
            vitality = hero.stats.vitality,
            dexterity = hero.stats.dexterity,
            stamina = hero.stats.stamina,
            selectedActivityType = state.selectedActivityType,
            durationMinutesInput = state.durationMinutesInput,
            isSubmitting = state.isSubmitting,
            importedTodayActivities = importedTodayEntries.size,
            importedTodaySteps = importedTodayEntries.sumOf { it.recorded.steps?.toLong() ?: 0L },
        )
    }

    private fun onChangeDurationMinutes(intent: HomeIntent.ChangeDurationMinutes) {
        _state.update {
            it.copy(durationMinutesInput = intent.value)
        }
    }

    private fun onSelectActivityType(intent: HomeIntent.SelectActivityType) {
        _state.update {
            it.copy(selectedActivityType = intent.type)
        }
    }

    private suspend fun onSubmitActivity() {
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
        const val HOME_LOADING_FAILED_MESSAGE = "Failed to load home state."
        const val INVALID_DURATION_MESSAGE = "Duration must be between 1 and 1440 minutes."
        const val ACTIVITY_LOGGING_FAILED_MESSAGE = "Failed to log activity."
    }
}
