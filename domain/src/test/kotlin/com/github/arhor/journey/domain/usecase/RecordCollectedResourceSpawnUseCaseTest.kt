package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.CollectedResourceSpawn
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroEnergy
import com.github.arhor.journey.domain.model.HeroStats
import com.github.arhor.journey.domain.model.Progression
import com.github.arhor.journey.domain.repository.CollectedResourceSpawnRepository
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

class RecordCollectedResourceSpawnUseCaseTest {

    @Test
    fun `invoke should record claim for the current hero with the current timestamp`() = runTest {
        // Given
        val hero = hero(id = "player")
        val now = Instant.parse("2026-03-12T11:00:00Z")
        val repository = FakeCollectedResourceSpawnRepository(recordResult = true)
        val subject = RecordCollectedResourceSpawnUseCase(
            heroRepository = FakeHeroRepository(hero),
            collectedResourceSpawnRepository = repository,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        // When
        val actual = subject(
            spawnId = "spawn-11",
            resourceTypeId = "wood",
        )

        // Then
        actual shouldBe true
        repository.lastHeroId shouldBe hero.id
        repository.lastSpawnId shouldBe "spawn-11"
        repository.lastResourceTypeId shouldBe "wood"
        repository.lastCollectedAt shouldBe now
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

    private class FakeCollectedResourceSpawnRepository(
        private val recordResult: Boolean,
    ) : CollectedResourceSpawnRepository {
        var lastHeroId: String? = null
        var lastSpawnId: String? = null
        var lastResourceTypeId: String? = null
        var lastCollectedAt: Instant? = null

        override fun observeAll(heroId: String): Flow<List<CollectedResourceSpawn>> = emptyFlow()

        override suspend fun isCollected(
            heroId: String,
            spawnId: String,
        ): Boolean = false

        override suspend fun recordClaim(
            heroId: String,
            spawnId: String,
            resourceTypeId: String,
            collectedAt: Instant,
        ): Boolean {
            lastHeroId = heroId
            lastSpawnId = spawnId
            lastResourceTypeId = resourceTypeId
            lastCollectedAt = collectedAt
            return recordResult
        }
    }
}
