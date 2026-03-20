package com.github.arhor.journey.feature.hero

import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.domain.internal.ProgressionPolicy
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroResource
import com.github.arhor.journey.domain.model.HeroStats
import com.github.arhor.journey.domain.model.Progression
import com.github.arhor.journey.domain.usecase.ObserveHeroResourcesUseCase
import com.github.arhor.journey.domain.usecase.ObserveHeroUseCase
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import java.time.Instant

class HeroViewModelTest {

    @Test
    fun `uiState should expose wood coal and stone resource amounts when hero resources are emitted`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))

            // Given
            val hero = hero()
            val observeHero = mockk<ObserveHeroUseCase>()
            val observeHeroResources = mockk<ObserveHeroResourcesUseCase>()
            every { observeHero() } returns flowOf(hero)
            every { observeHeroResources() } returns flowOf(
                listOf(
                    HeroResource(
                        heroId = hero.id,
                        resourceTypeId = ResourceType.WOOD.typeId,
                        amount = 7,
                        updatedAt = Instant.parse("2026-03-01T12:00:00Z"),
                    ),
                ),
            )
            val viewModel = HeroViewModel(
                observeCurrentHero = observeHero,
                observeHeroResources = observeHeroResources,
                progressionPolicy = ProgressionPolicy(),
            )

            try {
                // When
                val actual = viewModel.uiState.first { it is HeroUiState.Content } as HeroUiState.Content

                // Then
                actual.resources shouldBe listOf(
                    HeroResourceAmountUiModel(
                        resourceType = ResourceType.WOOD,
                        amount = 7,
                    ),
                    HeroResourceAmountUiModel(
                        resourceType = ResourceType.COAL,
                        amount = 0,
                    ),
                    HeroResourceAmountUiModel(
                        resourceType = ResourceType.STONE,
                        amount = 0,
                    ),
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun hero(): Hero = Hero(
        id = "hero-1",
        name = "Aster",
        stats = HeroStats(
            strength = 10,
            vitality = 12,
            dexterity = 8,
            stamina = 11,
        ),
        progression = Progression(
            level = 4,
            xpInLevel = 350,
        ),
        createdAt = Instant.parse("2026-03-01T12:00:00Z"),
        updatedAt = Instant.parse("2026-03-01T12:00:00Z"),
    )
}
