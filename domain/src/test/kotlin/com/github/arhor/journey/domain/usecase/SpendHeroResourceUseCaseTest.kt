package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroEnergy
import com.github.arhor.journey.domain.model.HeroResource
import com.github.arhor.journey.domain.model.HeroStats
import com.github.arhor.journey.domain.model.Progression
import com.github.arhor.journey.domain.model.error.HeroResourcesError
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

class SpendHeroResourceUseCaseTest {

    @Test
    fun `invoke should propagate insufficient amount failure for the current hero`() = runTest {
        // Given
        val hero = hero(id = "player")
        val now = Instant.parse("2026-03-12T10:00:00Z")
        val resourceTypeId = "ore"
        val expected = Output.Failure(
            HeroResourcesError.InsufficientAmount(
                resourceTypeId = resourceTypeId,
                requestedAmount = 2,
                availableAmount = 1,
            ),
        )
        val repository = FakeHeroResourcesRepository(spendResult = expected)
        val subject = SpendHeroResourceUseCase(
            heroRepository = FakeHeroRepository(hero),
            heroInventoryRepository = repository,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        // When
        val actual = subject(
            resourceTypeId = resourceTypeId,
            amount = 2,
        )

        // Then
        actual shouldBe expected
        repository.lastSpendHeroId shouldBe hero.id
        repository.lastSpendResourceTypeId shouldBe resourceTypeId
        repository.lastSpendAmount shouldBe 2
        repository.lastSpendUpdatedAt shouldBe now
    }

    private fun hero(id: String): Hero =
        Hero(
            id = id,
            name = "Adventurer",
            stats = HeroStats(
                strength = 1,
                vitality = 1,
                dexterity = 1,
                stamina = 1,
            ),
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
        private val spendResult: Output<HeroResource, HeroResourcesError>,
    ) : HeroInventoryRepository {
        var lastSpendHeroId: String? = null
        var lastSpendResourceTypeId: String? = null
        var lastSpendAmount: Int? = null
        var lastSpendUpdatedAt: Instant? = null

        override fun observeAll(heroId: String): Flow<List<HeroResource>> = emptyFlow()

        override fun observeAmount(
            heroId: String,
            resourceTypeId: String,
        ): Flow<Int> = emptyFlow()

        override suspend fun getAmount(
            heroId: String,
            resourceTypeId: String,
        ): Int =
            when (val result = spendResult) {
                is Output.Failure -> when (val error = result.error) {
                    is HeroResourcesError.InsufficientAmount -> error.availableAmount
                }
                is Output.Success -> result.value.amount
            }

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
        ): HeroResource? {
            lastSpendHeroId = heroId
            lastSpendResourceTypeId = resourceTypeId
            lastSpendAmount = amount
            lastSpendUpdatedAt = updatedAt
            return (spendResult as? Output.Success)?.value
        }
    }
}
