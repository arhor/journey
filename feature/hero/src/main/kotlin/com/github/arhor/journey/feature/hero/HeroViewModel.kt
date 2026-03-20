package com.github.arhor.journey.feature.hero

import androidx.compose.runtime.Stable
import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.core.ui.MviViewModel
import com.github.arhor.journey.domain.internal.ProgressionPolicy
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroResource
import com.github.arhor.journey.domain.usecase.ObserveHeroResourcesUseCase
import com.github.arhor.journey.domain.usecase.ObserveHeroUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

@Stable
@HiltViewModel
class HeroViewModel @Inject constructor(
    private val observeCurrentHero: ObserveHeroUseCase,
    private val observeHeroResources: ObserveHeroResourcesUseCase,
    private val progressionPolicy: ProgressionPolicy,
) : MviViewModel<HeroUiState, HeroEffect, HeroIntent>(
    initialState = HeroUiState.Loading,
) {

    override fun buildUiState(): Flow<HeroUiState> =
        combine(observeCurrentHero(), observeHeroResources()) { hero, resources ->
            intoUiState(hero = hero, resources = resources)
        }
            .catch { emit(HeroUiState.Failure(errorMessage = it.message ?: HERO_LOADING_FAILED_MESSAGE)) }
            .distinctUntilChanged()

    override suspend fun handleIntent(intent: HeroIntent) {

    }

    /* ------------------------------------------ Internal implementation ------------------------------------------- */

    private fun intoUiState(
        hero: Hero,
        resources: List<HeroResource>,
    ): HeroUiState {
        val resourceAmounts = resourcesByType(resources)

        return HeroUiState.Content(
            heroName = hero.name,
            level = hero.progression.level,
            xpInLevel = hero.progression.xpInLevel,
            xpToNextLevel = progressionPolicy.xpToNextLevel(hero.progression.level),
            strength = hero.stats.strength,
            vitality = hero.stats.vitality,
            dexterity = hero.stats.dexterity,
            stamina = hero.stats.stamina,
            resources = ResourceType.entries.map {
                HeroResourceAmountUiModel(
                    resourceType = it,
                    amount = resourceAmounts[it.typeId] ?: 0,
                )
            },
        )
    }

    private fun resourcesByType(resources: List<HeroResource>): Map<String, Int> =
        resources.associate { resource ->
            resource.resourceTypeId to resource.amount
        }

    private companion object {
        const val HERO_LOADING_FAILED_MESSAGE = "Failed to load hero state."
    }
}
