package com.github.arhor.journey.feature.hero

import androidx.compose.runtime.Stable
import com.github.arhor.journey.core.ui.MviViewModel
import com.github.arhor.journey.domain.internal.ProgressionPolicy
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.usecase.ObserveHeroUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@Stable
@HiltViewModel
class HeroViewModel @Inject constructor(
    private val observeCurrentHero: ObserveHeroUseCase,
    private val progressionPolicy: ProgressionPolicy,
) : MviViewModel<HeroUiState, HeroEffect, HeroIntent>(
    initialState = HeroUiState.Loading,
) {

    override fun buildUiState(): Flow<HeroUiState> =
        observeCurrentHero()
            .map(::intoUiState)
            .catch { emit(HeroUiState.Failure(errorMessage = it.message ?: HERO_LOADING_FAILED_MESSAGE)) }
            .distinctUntilChanged()

    override suspend fun handleIntent(intent: HeroIntent) {

    }

    /* ------------------------------------------ Internal implementation ------------------------------------------- */

    private fun intoUiState(
        hero: Hero,
    ): HeroUiState = HeroUiState.Content(
        heroName = hero.name,
        level = hero.progression.level,
        xpInLevel = hero.progression.xpInLevel,
        xpToNextLevel = progressionPolicy.xpToNextLevel(hero.progression.level),
        strength = hero.stats.strength,
        vitality = hero.stats.vitality,
        dexterity = hero.stats.dexterity,
        stamina = hero.stats.stamina,
    )

    private companion object {
        const val HERO_LOADING_FAILED_MESSAGE = "Failed to load hero state."
    }
}
