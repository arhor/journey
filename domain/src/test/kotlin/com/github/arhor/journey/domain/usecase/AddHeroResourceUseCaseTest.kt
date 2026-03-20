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
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AddHeroResourceUseCaseTest {

    @Test
    fun `invoke should add resources for the current hero using the current time`() = runTest {
        // Given
        val hero = hero(id = "player")
        val now = Instant.parse("2026-03-12T09:00:00Z")
        val repository = FakeHeroResourcesRepository(
            addResult = HeroResource(
                heroId = hero.id,
                resourceTypeId = "wood",
                amount = 4,
                updatedAt = now,
            ),
        )
        val subject = AddHeroResourceUseCase(
            heroRepository = FakeHeroRepository(hero),
            heroInventoryRepository = repository,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        // When
        val actual = subject(
            resourceTypeId = "wood",
            amount = 3,
        )

        // Then
        actual shouldBe repository.addResult
        repository.lastAddHeroId shouldBe hero.id
        repository.lastAddResourceTypeId shouldBe "wood"
        repository.lastAddAmount shouldBe 3
        repository.lastAddUpdatedAt shouldBe now
    }

    private fun hero(id: String): Hero =
        Hero(
            id = id,
            name = "Adventurer",
            progression = Progression(level = 1, xpInLevel = 0L),
            energy = HeroEnergy(max = 100),
            createdAt = Instant.parse("2026-03-12T08:00:00Z"),
            updatedAt = Instant.parse("2026-03-12T08:00:00Z"),
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
        val addResult: HeroResource,
    ) : HeroInventoryRepository {
        var lastAddHeroId: String? = null
        var lastAddResourceTypeId: String? = null
        var lastAddAmount: Int? = null
        var lastAddUpdatedAt: Instant? = null

        override fun observeAll(heroId: String): Flow<List<HeroResource>> = emptyFlow()

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
        ): HeroResource {
            lastAddHeroId = heroId
            lastAddResourceTypeId = resourceTypeId
            lastAddAmount = amount
            lastAddUpdatedAt = updatedAt
            return addResult
        }

        override suspend fun spendAmount(
            heroId: String,
            resourceTypeId: String,
            amount: Int,
            updatedAt: Instant,
        ): Nothing = throw NotImplementedError()
    }
}
