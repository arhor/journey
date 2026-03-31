package com.github.arhor.journey.feature.map

import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroResource
import com.github.arhor.journey.domain.model.Progression
import com.github.arhor.journey.domain.usecase.ObserveHeroResourcesUseCase
import com.github.arhor.journey.domain.usecase.ObserveHeroUseCase
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import java.time.Instant

class MapHudViewModelTest {

    @Test
    fun `uiState should expose hero initial level label and compact resource amounts when hero data is emitted`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        try {
            val observeHero = mockk<ObserveHeroUseCase>()
            val observeHeroResources = mockk<ObserveHeroResourcesUseCase>()
            val hero = hero()

            every { observeHero() } returns flowOf(hero)
            every { observeHeroResources() } returns flowOf(
                listOf(
                    HeroResource(
                        heroId = hero.id,
                        resourceTypeId = ResourceType.SCRAP.typeId,
                        amount = 1_250,
                        updatedAt = Instant.parse("2026-03-01T12:00:00Z"),
                    ),
                    HeroResource(
                        heroId = hero.id,
                        resourceTypeId = ResourceType.FUEL.typeId,
                        amount = 1_300_000,
                        updatedAt = Instant.parse("2026-03-01T12:05:00Z"),
                    ),
                ),
            )
            val viewModel = MapHudViewModel(
                observeHero = observeHero,
                observeHeroResources = observeHeroResources,
            )

            // When
            val actualDeferred = async { viewModel.uiState.first { it is MapHudUiState.Content } }
            advanceUntilIdle()
            val actual = actualDeferred.await()

            // Then
            actual shouldBe MapHudUiState.Content(
                heroInitial = "A",
                levelLabel = "Lv 4",
                resources = listOf(
                    MapHudResourceUiModel(
                        resourceType = ResourceType.SCRAP,
                        amount = 1_250,
                        amountLabel = "1.2K",
                    ),
                    MapHudResourceUiModel(
                        resourceType = ResourceType.COMPONENTS,
                        amount = 0,
                        amountLabel = "0",
                    ),
                    MapHudResourceUiModel(
                        resourceType = ResourceType.FUEL,
                        amount = 1_300_000,
                        amountLabel = "1.3M",
                    ),
                ),
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `uiState should expose unavailable when hero stream throws`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        try {
            val observeHero = mockk<ObserveHeroUseCase>()
            val observeHeroResources = mockk<ObserveHeroResourcesUseCase>()

            every { observeHero() } returns flow {
                throw IllegalStateException("Hero stream crashed.")
            }
            every { observeHeroResources() } returns flowOf(emptyList())
            val viewModel = MapHudViewModel(
                observeHero = observeHero,
                observeHeroResources = observeHeroResources,
            )

            // When
            val actualDeferred = async { viewModel.uiState.first { it !is MapHudUiState.Loading } }
            advanceUntilIdle()
            val actual = actualDeferred.await()

            // Then
            actual shouldBe MapHudUiState.Unavailable
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `formatResourceAmount should abbreviate thousands and millions when values exceed compact thresholds`() {
        // Given
        val expected = listOf("999", "1.2K", "12K", "1.3M")

        // When
        val actual = listOf(
            formatResourceAmount(999),
            formatResourceAmount(1_250),
            formatResourceAmount(12_300),
            formatResourceAmount(1_300_000),
        )

        // Then
        actual shouldBe expected
    }

    private fun hero(): Hero = Hero(
        id = "hero-1",
        name = "Aster",
        progression = Progression(
            level = 4,
            xpInLevel = 350,
        ),
        createdAt = Instant.parse("2026-03-01T12:00:00Z"),
        updatedAt = Instant.parse("2026-03-01T12:00:00Z"),
    )
}
