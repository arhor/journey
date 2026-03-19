package com.github.arhor.journey.data.repository

import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.ResourceSpawnQuery
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class DeterministicResourceSpawnRepositoryTest {

    private val subject = DeterministicResourceSpawnRepository()

    @Test
    fun `getActiveSpawns should stay deterministic for the same area and day`() = runTest {
        // Given
        val query = ResourceSpawnQuery(
            at = Instant.parse("2026-03-19T10:00:00Z"),
            bounds = GeoBounds(
                south = 49.0000,
                west = 24.0000,
                north = 49.0100,
                east = 24.0100,
            ),
        )

        // When
        val first = subject.getActiveSpawns(query)
        val second = subject.getActiveSpawns(query)

        // Then
        first shouldBe second
    }

    @Test
    fun `getActiveSpawns should return stable spawn ids for a fixed query`() = runTest {
        // Given
        val query = ResourceSpawnQuery(
            at = Instant.parse("2026-03-19T10:00:00Z"),
            bounds = GeoBounds(
                south = 49.0000,
                west = 24.0000,
                north = 49.0050,
                east = 24.0050,
            ),
        )

        // When
        val spawns = subject.getActiveSpawns(query)

        // Then
        spawns shouldHaveSize 2
        val ids = spawns.map { it.id }
        val expectedEpochDay = query.at.atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
        ids.distinct() shouldContainExactly ids
        ids.all { it.startsWith("resource-spawn:v1:$expectedEpochDay:") } shouldBe true
        ids.all { it.split(':').size == 7 } shouldBe true
    }

    @Test
    fun `getActiveSpawn should resolve the same spawn by id for the same day and hide it on another day`() = runTest {
        // Given
        val sameDay = Instant.parse("2026-03-19T10:00:00Z")
        val nextDay = Instant.parse("2026-03-20T10:00:00Z")
        val spawnId = subject.getActiveSpawns(
            ResourceSpawnQuery(
                at = sameDay,
                bounds = GeoBounds(
                    south = 49.0000,
                    west = 24.0000,
                    north = 49.0050,
                    east = 24.0050,
                ),
            ),
        ).first().id

        // When
        val available = subject.getActiveSpawn(spawnId = spawnId, at = sameDay)
        val unavailable = subject.getActiveSpawn(spawnId = spawnId, at = nextDay)

        // Then
        available?.id shouldBe spawnId
        unavailable shouldBe null
    }

    @Test
    fun `getActiveSpawns should filter nearby queries by center and radius`() = runTest {
        // Given
        val day = Instant.parse("2026-03-19T10:00:00Z")
        val visibleSpawns = subject.getActiveSpawns(
            ResourceSpawnQuery(
                at = day,
                bounds = GeoBounds(
                    south = 49.0000,
                    west = 24.0000,
                    north = 49.0100,
                    east = 24.0100,
                ),
            ),
        )
        val targetSpawn = visibleSpawns.first()

        // When
        val nearbySpawns = subject.getActiveSpawns(
            ResourceSpawnQuery(
                at = day,
                center = targetSpawn.position,
                radiusMeters = 1.0,
            ),
        )

        // Then
        nearbySpawns shouldContainExactly listOf(targetSpawn)
    }
}
