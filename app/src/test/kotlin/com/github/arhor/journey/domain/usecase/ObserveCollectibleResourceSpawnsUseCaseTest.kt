package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.CollectedResourceSpawn
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroEnergy
import com.github.arhor.journey.domain.model.Progression
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.ResourceSpawnQuery
import com.github.arhor.journey.domain.repository.CollectedResourceSpawnRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import com.github.arhor.journey.domain.repository.ResourceSpawnRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class ObserveCollectibleResourceSpawnsUseCaseTest {

    @Test
    fun `invoke should requery active resources after UTC day rollover while bounds are unchanged`() = runTest {
        // Given
        val clock = MutableClock(Instant.parse("2026-03-19T23:59:58Z"))
        val resourceSpawnRepository = FakeResourceSpawnRepository()
        val subject = ObserveCollectibleResourceSpawnsUseCase(
            heroRepository = FakeHeroRepository(hero(id = "player")),
            collectedResourceSpawnRepository = FakeCollectedResourceSpawnRepository(),
            resourceSpawnRepository = resourceSpawnRepository,
            clock = clock,
        )
        val queryBounds = GeoBounds(
            south = 49.0000,
            west = 24.0000,
            north = 49.0100,
            east = 24.0100,
        )
        val emissions = mutableListOf<Output<List<ResourceSpawn>, *>>()

        // When
        val collectJob = launch {
            subject(queryBounds)
                .take(2)
                .toList(emissions)
        }
        runCurrent()

        clock.currentInstant = Instant.parse("2026-03-20T00:00:00Z")
        advanceTimeBy(2_000L)
        runCurrent()

        // Then
        emissions shouldHaveSize 2
        resourceSpawnRepository.queries.map { query ->
            query.at.atZone(ZoneOffset.UTC).toLocalDate()
        } shouldBe listOf(
            Instant.parse("2026-03-19T00:00:00Z").atZone(ZoneOffset.UTC).toLocalDate(),
            Instant.parse("2026-03-20T00:00:00Z").atZone(ZoneOffset.UTC).toLocalDate(),
        )

        collectJob.cancel()
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

    private class MutableClock(
        var currentInstant: Instant,
        private val zone: ZoneId = ZoneOffset.UTC,
    ) : Clock() {

        override fun instant(): Instant = currentInstant

        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock =
            MutableClock(
                currentInstant = currentInstant,
                zone = zone,
            )
    }

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

    private class FakeCollectedResourceSpawnRepository : CollectedResourceSpawnRepository {

        override fun observeAll(heroId: String): Flow<List<CollectedResourceSpawn>> = flowOf(emptyList())

        override suspend fun isCollected(
            heroId: String,
            spawnId: String,
        ): Boolean = false

        override suspend fun markCollected(
            heroId: String,
            spawnId: String,
            resourceTypeId: String,
            collectedAt: Instant,
        ): Boolean = true
    }

    private class FakeResourceSpawnRepository : ResourceSpawnRepository {
        val queries = mutableListOf<ResourceSpawnQuery>()

        override fun observeActiveSpawns(query: ResourceSpawnQuery): Flow<List<ResourceSpawn>> {
            queries += query
            return flowOf(listOf(spawn(query.at)))
        }

        override suspend fun getActiveSpawns(query: ResourceSpawnQuery): List<ResourceSpawn> = listOf(spawn(query.at))

        override suspend fun getActiveSpawn(
            spawnId: String,
            at: Instant,
        ): ResourceSpawn? = spawn(at).takeIf { it.id == spawnId }

        private fun spawn(activeAt: Instant): ResourceSpawn =
            ResourceSpawn(
                id = "resource:${activeAt.atZone(ZoneOffset.UTC).toLocalDate()}",
                typeId = "scrap",
                position = GeoPoint(lat = 49.005, lon = 24.005),
                collectionRadiusMeters = 25.0,
                availableFrom = activeAt,
                availableUntil = activeAt.plusSeconds(60),
            )
    }
}
