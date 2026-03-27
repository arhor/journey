package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroEnergy
import com.github.arhor.journey.domain.model.HeroResource
import com.github.arhor.journey.domain.model.Progression
import com.github.arhor.journey.domain.repository.HeroInventoryRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class ObserveHeroResourcesUseCaseTest {

    @Test
    fun `invoke should observe resources for the current hero id`() = runTest {
        // Given
        val hero = hero(id = "player")
        val expected = listOf(
            HeroResource(
                heroId = hero.id,
                resourceTypeId = "wood",
                amount = 3,
                updatedAt = Instant.parse("2026-03-12T08:00:00Z"),
            ),
        )
        val heroRepository = FakeHeroRepository(hero)
        val resourcesRepository = FakeHeroResourcesRepository(observedResources = expected)
        val subject = ObserveHeroResourcesUseCase(
            heroRepository = heroRepository,
            heroInventoryRepository = resourcesRepository,
        )

        // When
        val actual = subject().first()

        // Then
        actual shouldBe expected
        resourcesRepository.lastObservedHeroId shouldBe hero.id
    }

    private fun hero(id: String): Hero =
        Hero(
            id = id,
            name = "Adventurer",
            progression = Progression(level = 1, xpInLevel = 0L),
            energy = HeroEnergy(max = 100),
            createdAt = Instant.parse("2026-03-12T07:30:00Z"),
            updatedAt = Instant.parse("2026-03-12T07:30:00Z"),
        )

    private class FakeHeroRepository(
        hero: Hero,
    ) : HeroRepository {
        private val flow = MutableStateFlow(hero)

        override fun observeCurrentHero(): Flow<Hero> = flow

        override suspend fun getCurrentHero(): Hero = flow.value

        override suspend fun upsert(hero: Hero) {
            flow.value = hero
        }
    }

    private class FakeHeroResourcesRepository(
        private val observedResources: List<HeroResource>,
    ) : HeroInventoryRepository {
        var lastObservedHeroId: String? = null

        override fun observeAll(heroId: String): Flow<List<HeroResource>> {
            lastObservedHeroId = heroId
            return flowOf(observedResources)
        }

        override fun observeAmount(
            heroId: String,
            resourceTypeId: String,
        ): Flow<Int> = emptyFlow()

        override suspend fun getAmount(
            heroId: String,
            resourceTypeId: String,
        ): Int = 0

        override suspend fun setAmount(
            heroId: String,
            resourceTypeId: String,
            amount: Int,
            updatedAt: Instant,
        ): HeroResource = throw NotImplementedError()

        override suspend fun addAmount(
            heroId: String,
            resourceTypeId: String,
            amount: Int,
            updatedAt: Instant,
        ): HeroResource = throw NotImplementedError()

        override suspend fun spendAmount(
            heroId: String,
            resourceTypeId: String,
            amount: Int,
            updatedAt: Instant,
        ): HeroResource? = throw NotImplementedError()
    }
}
