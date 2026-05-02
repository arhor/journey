package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.HeroDao
import com.github.arhor.journey.data.local.db.entity.HeroEntity
import com.github.arhor.journey.data.local.seed.DefaultHeroSeed
import com.github.arhor.journey.data.mapper.toEntity
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroEnergy
import com.github.arhor.journey.domain.model.Progression
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RoomHeroRepositoryTest {

    @Test
    fun `getCurrentHero should seed and return default hero when storage is empty`() = runTest {
        // Given
        val dao = FakeHeroDao(initialHero = null)
        val fixedNow = Instant.parse("2026-03-01T09:00:00Z")
        val subject = RoomHeroRepository(
            heroDao = dao,
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
        )

        // When
        val actual = subject.getCurrentHero()

        // Then
        actual.id shouldBe DefaultHeroSeed.CURRENT_HERO_ID
        actual.createdAt shouldBe fixedNow
        actual.updatedAt shouldBe fixedNow
        dao.upsertedEntities shouldHaveSize 1
        dao.upsertedEntities.first().id shouldBe DefaultHeroSeed.CURRENT_HERO_ID
    }

    @Test
    fun `getCurrentHero should return existing hero when default hero already exists`() = runTest {
        // Given
        val existingHero = hero(
            id = DefaultHeroSeed.CURRENT_HERO_ID,
            name = "Existing",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T01:00:00Z"),
        ).toEntity()
        val dao = FakeHeroDao(initialHero = existingHero)
        val subject = RoomHeroRepository(
            heroDao = dao,
            clock = Clock.fixed(Instant.parse("2026-03-01T09:00:00Z"), ZoneOffset.UTC),
        )

        // When
        val actual = subject.getCurrentHero()

        // Then
        actual.id shouldBe DefaultHeroSeed.CURRENT_HERO_ID
        actual.name shouldBe "Existing"
        dao.upsertedEntities.shouldBeEmpty()
    }

    @Test
    fun `observeCurrentHero should emit seeded hero when flow starts with null`() = runTest {
        // Given
        val dao = FakeHeroDao(initialHero = null)
        val fixedNow = Instant.parse("2026-03-02T10:15:00Z")
        val subject = RoomHeroRepository(
            heroDao = dao,
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
        )

        // When
        val actual = subject.observeCurrentHero().first()

        // Then
        actual.id shouldBe DefaultHeroSeed.CURRENT_HERO_ID
        actual.createdAt shouldBe fixedNow
        dao.upsertedEntities shouldHaveSize 1
    }

    private fun hero(
        id: String,
        name: String,
        createdAt: Instant,
        updatedAt: Instant,
    ): Hero =
        Hero(
            id = id,
            name = name,
            progression = Progression(level = 3, xpInLevel = 250L),
            energy = HeroEnergy(now = 40, max = 90),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private class FakeHeroDao(
        initialHero: HeroEntity?,
    ) : HeroDao {
        private val heroById = mutableMapOf<String, HeroEntity>()
        private val flowById = mutableMapOf<String, MutableStateFlow<HeroEntity?>>()

        val upsertedEntities = mutableListOf<HeroEntity>()

        init {
            if (initialHero != null) {
                heroById[initialHero.id] = initialHero
                flowById[initialHero.id] = MutableStateFlow(initialHero)
            }
        }

        override fun observeById(id: String): Flow<HeroEntity?> =
            flowById.getOrPut(id) { MutableStateFlow(heroById[id]) }

        override suspend fun getById(id: String): HeroEntity? = heroById[id]

        override suspend fun upsert(entity: HeroEntity) {
            heroById[entity.id] = entity
            upsertedEntities += entity
            flowById.getOrPut(entity.id) { MutableStateFlow(null) }.value = entity
        }
    }
}
