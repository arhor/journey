package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.CollectedResourceSpawnReward
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroEnergy
import com.github.arhor.journey.domain.model.Progression
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.error.CollectResourceSpawnError
import com.github.arhor.journey.domain.repository.HeroRepository
import com.github.arhor.journey.domain.repository.ResourceSpawnRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CollectNearbyResourceSpawnsUseCaseTest {

    @Test
    fun `invoke should preserve per-spawn outputs when nearby collection mixes success and business failure`() = runTest {
        // Given
        val now = Instant.parse("2026-03-19T10:00:00Z")
        val hero = hero()
        val firstSpawn = resourceSpawn(
            id = "resource-spawn:v1:20527:10:20:0:scrap",
            resourceTypeId = "scrap",
        )
        val secondSpawn = resourceSpawn(
            id = "resource-spawn:v1:20527:10:20:1:fuel",
            resourceTypeId = "fuel",
        )
        val expectedSuccess = Output.Success(
            CollectedResourceSpawnReward(
                spawnId = firstSpawn.id,
                resourceTypeId = firstSpawn.typeId,
                amountAwarded = 1,
            ),
        )
        val expectedFailure = Output.Failure(
            CollectResourceSpawnError.AlreadyCollected(secondSpawn.id),
        )
        val heroRepository = mockk<HeroRepository>()
        val resourceSpawnRepository = mockk<ResourceSpawnRepository>()
        val collectResourceSpawn = mockk<CollectResourceSpawnUseCase>()
        val subject = CollectNearbyResourceSpawnsUseCase(
            heroRepository = heroRepository,
            resourceSpawnRepository = resourceSpawnRepository,
            collectResourceSpawn = collectResourceSpawn,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        val location = GeoPoint(lat = 49.0000, lon = 24.0000)

        coEvery { heroRepository.getCurrentHero() } returns hero
        coEvery { resourceSpawnRepository.getActiveSpawns(any()) } returns listOf(firstSpawn, secondSpawn)
        coEvery { collectResourceSpawn.collectSpawn(hero.id, firstSpawn, location, now) } returns expectedSuccess
        coEvery { collectResourceSpawn.collectSpawn(hero.id, secondSpawn, location, now) } returns expectedFailure

        // When
        val actual = subject(location)

        // Then
        actual shouldBe listOf(expectedSuccess, expectedFailure)
    }

    @Test
    fun `invoke should continue collecting remaining spawns when one spawn throws unexpectedly`() = runTest {
        // Given
        val now = Instant.parse("2026-03-19T10:00:00Z")
        val hero = hero()
        val firstSpawn = resourceSpawn(
            id = "resource-spawn:v1:20527:10:20:2:components",
            resourceTypeId = "components",
        )
        val secondSpawn = resourceSpawn(
            id = "resource-spawn:v1:20527:10:20:3:scrap",
            resourceTypeId = "scrap",
        )
        val exception = IllegalStateException("Collection failed.")
        val expectedSuccess = Output.Success(
            CollectedResourceSpawnReward(
                spawnId = secondSpawn.id,
                resourceTypeId = secondSpawn.typeId,
                amountAwarded = 1,
            ),
        )
        val heroRepository = mockk<HeroRepository>()
        val resourceSpawnRepository = mockk<ResourceSpawnRepository>()
        val collectResourceSpawn = mockk<CollectResourceSpawnUseCase>()
        val subject = CollectNearbyResourceSpawnsUseCase(
            heroRepository = heroRepository,
            resourceSpawnRepository = resourceSpawnRepository,
            collectResourceSpawn = collectResourceSpawn,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        val location = GeoPoint(lat = 49.0000, lon = 24.0000)

        coEvery { heroRepository.getCurrentHero() } returns hero
        coEvery { resourceSpawnRepository.getActiveSpawns(any()) } returns listOf(firstSpawn, secondSpawn)
        coEvery { collectResourceSpawn.collectSpawn(hero.id, firstSpawn, location, now) } throws exception
        coEvery { collectResourceSpawn.collectSpawn(hero.id, secondSpawn, location, now) } returns expectedSuccess

        // When
        val actual = subject(location)

        // Then
        actual shouldBe listOf(
            Output.Failure(
                CollectResourceSpawnError.Unexpected(
                    spawnId = firstSpawn.id,
                    cause = exception,
                ),
            ),
            expectedSuccess,
        )
        coVerify(exactly = 1) { collectResourceSpawn.collectSpawn(hero.id, secondSpawn, location, now) }
    }

    @Test
    fun `invoke should rethrow cancellation when per spawn collection is cancelled`() = runTest {
        // Given
        val now = Instant.parse("2026-03-19T10:00:00Z")
        val hero = hero()
        val firstSpawn = resourceSpawn(
            id = "resource-spawn:v1:20527:10:20:4:fuel",
            resourceTypeId = "fuel",
        )
        val secondSpawn = resourceSpawn(
            id = "resource-spawn:v1:20527:10:20:5:components",
            resourceTypeId = "components",
        )
        val cancellation = CancellationException("Batch cancelled.")
        val heroRepository = mockk<HeroRepository>()
        val resourceSpawnRepository = mockk<ResourceSpawnRepository>()
        val collectResourceSpawn = mockk<CollectResourceSpawnUseCase>()
        val subject = CollectNearbyResourceSpawnsUseCase(
            heroRepository = heroRepository,
            resourceSpawnRepository = resourceSpawnRepository,
            collectResourceSpawn = collectResourceSpawn,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        val location = GeoPoint(lat = 49.0000, lon = 24.0000)

        coEvery { heroRepository.getCurrentHero() } returns hero
        coEvery { resourceSpawnRepository.getActiveSpawns(any()) } returns listOf(firstSpawn, secondSpawn)
        coEvery { collectResourceSpawn.collectSpawn(hero.id, firstSpawn, location, now) } throws cancellation

        // When
        val actual = try {
            subject(location)
            null
        } catch (error: CancellationException) {
            error
        }

        // Then
        actual shouldBe cancellation
        coVerify(exactly = 0) { collectResourceSpawn.collectSpawn(hero.id, secondSpawn, location, now) }
    }

    private fun hero(): Hero =
        Hero(
            id = "player",
            name = "Adventurer",
            progression = Progression(level = 1, xpInLevel = 0L),
            energy = HeroEnergy(max = 100),
            createdAt = Instant.parse("2026-03-19T09:00:00Z"),
            updatedAt = Instant.parse("2026-03-19T09:00:00Z"),
        )

    private fun resourceSpawn(
        id: String,
        resourceTypeId: String,
    ): ResourceSpawn =
        ResourceSpawn(
            id = id,
            typeId = resourceTypeId,
            position = GeoPoint(lat = 49.0000, lon = 24.0000),
            collectionRadiusMeters = 25.0,
            availableFrom = Instant.parse("2026-03-19T00:00:00Z"),
            availableUntil = Instant.parse("2026-03-20T00:00:00Z"),
        )
}
