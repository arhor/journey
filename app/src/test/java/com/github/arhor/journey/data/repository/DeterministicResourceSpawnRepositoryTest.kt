package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.mapobject.DeterministicResourceSpawnGenerator
import com.github.arhor.journey.data.mapobject.InMemoryMapObjectChunkCache
import com.github.arhor.journey.data.mapobject.LocalGeneratedMapObjectAreaSource
import com.github.arhor.journey.data.mapobject.MapObjectAreaStore
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.ResourceSpawnQuery
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class DeterministicResourceSpawnRepositoryTest {

    private val generator = DeterministicResourceSpawnGenerator()

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
        val first = generator.activeSpawns(query)
        val second = generator.activeSpawns(query)

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
        val spawns = generator.activeSpawns(query)

        // Then
        spawns shouldHaveSize 2
        val ids = spawns.map { it.id }
        val expectedEpochDay = query.at.atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
        ids.distinct() shouldContainExactly ids
        ids shouldContainExactly listOf(
            "resource-spawn:v1:$expectedEpochDay:4800:9800:0:scrap",
            "resource-spawn:v1:$expectedEpochDay:4800:9800:1:fuel",
        )
        spawns.map { it.typeId } shouldContainExactly listOf("scrap", "fuel")
    }

    @Test
    fun `getActiveSpawns should expose only the new public resource ids`() = runTest {
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
        val spawns = generator.activeSpawns(query)

        // Then
        spawns.map { it.typeId }.all { it in setOf("scrap", "components", "fuel") } shouldBe true
    }

    @Test
    fun `getActiveSpawn should resolve the same spawn by id for the same day and hide it on another day`() = runTest {
        // Given
        val sameDay = Instant.parse("2026-03-19T10:00:00Z")
        val nextDay = Instant.parse("2026-03-20T10:00:00Z")
        val spawnId = generator.activeSpawns(
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
        val available = generator.activeSpawnById(spawnId = spawnId, at = sameDay)
        val unavailable = generator.activeSpawnById(spawnId = spawnId, at = nextDay)

        // Then
        available?.id shouldBe spawnId
        unavailable shouldBe null
    }

    @Test
    fun `repository getActiveSpawn should resolve id from cold cache through direct source lookup`() = runTest {
        // Given
        val day = Instant.parse("2026-03-19T10:00:00Z")
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val subject = DeterministicResourceSpawnRepository(
            areaStore = MapObjectAreaStore(
                source = LocalGeneratedMapObjectAreaSource(
                    resourceSpawnGenerator = generator,
                    defaultDispatcher = dispatcher,
                ),
                cache = InMemoryMapObjectChunkCache(),
                defaultDispatcher = dispatcher,
            ),
        )
        val spawn = generator.activeSpawns(
            ResourceSpawnQuery(
                at = day,
                bounds = GeoBounds(
                    south = 49.0000,
                    west = 24.0000,
                    north = 49.0050,
                    east = 24.0050,
                ),
            ),
        ).first()

        // When
        val actual = subject.getActiveSpawn(spawnId = spawn.id, at = day)

        // Then
        actual shouldBe spawn
    }

    @Test
    fun `getActiveSpawns should filter nearby queries by center and radius`() = runTest {
        // Given
        val day = Instant.parse("2026-03-19T10:00:00Z")
        val visibleSpawns = generator.activeSpawns(
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
        val nearbySpawns = generator.activeSpawns(
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
