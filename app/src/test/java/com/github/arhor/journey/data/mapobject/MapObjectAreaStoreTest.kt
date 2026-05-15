package com.github.arhor.journey.data.mapobject

import com.github.arhor.journey.domain.internal.bounds
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.ResourceSpawnQuery
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class MapObjectAreaStoreTest {

    @Test
    fun `observeActiveResourceSpawns should emit cached resources first then refreshed resources`() = runTest {
        // Given
        val source = FakeMapObjectAreaSource()
        val subject = createSubject(source)
        val firstTile = MapTile(zoom = 15, x = 18_000, y = 11_000)
        val firstBounds = bounds(firstTile)
        val combinedBounds = bounds(
            ExplorationTileRange(
                zoom = 15,
                minX = firstTile.x,
                maxX = firstTile.x + 1,
                minY = firstTile.y,
                maxY = firstTile.y,
            ),
        )
        val activeAt = Instant.parse("2026-03-19T10:00:00Z")
        subject.getActiveResourceSpawns(ResourceSpawnQuery(at = activeAt, bounds = firstBounds))

        // When
        val actual = subject.observeActiveResourceSpawns(
            ResourceSpawnQuery(
                at = activeAt,
                bounds = combinedBounds,
            ),
        )
            .take(2)
            .toList()

        // Then
        actual shouldHaveSize 2
        actual[0].map(ResourceSpawn::id) shouldBe listOf(source.resourceId(firstBounds))
        actual[1].map(ResourceSpawn::id) shouldBe listOf(
            source.resourceId(firstBounds),
            source.resourceId(
                bounds(
                    MapTile(
                        zoom = firstTile.zoom,
                        x = firstTile.x + 1,
                        y = firstTile.y,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `observeActiveResourceSpawns should emit cached resources once when request is fully cached`() = runTest {
        // Given
        val source = FakeMapObjectAreaSource()
        val subject = createSubject(source)
        val queryBounds = bounds(MapTile(zoom = 15, x = 18_000, y = 11_000))
        val activeAt = Instant.parse("2026-03-19T10:00:00Z")
        val initial = subject.getActiveResourceSpawns(ResourceSpawnQuery(at = activeAt, bounds = queryBounds))
        val fetchCountAfterPreload = source.fetchAreaRequests.size

        // When
        val actual = subject.observeActiveResourceSpawns(
            ResourceSpawnQuery(
                at = activeAt,
                bounds = queryBounds,
            ),
        ).toList()

        // Then
        actual shouldBe listOf(initial)
        source.fetchAreaRequests.size shouldBe fetchCountAfterPreload
    }

    private fun TestScope.createSubject(
        source: FakeMapObjectAreaSource,
    ): MapObjectAreaStore {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return MapObjectAreaStore(
            source = source,
            cache = InMemoryMapObjectChunkCache(),
            defaultDispatcher = dispatcher,
        )
    }

    private class FakeMapObjectAreaSource(
        private val responseOverride: MapObjectAreaResponse? = null,
    ) : MapObjectAreaSource {
        val fetchAreaRequests = mutableListOf<GeoBounds>()

        override suspend fun fetchArea(
            bounds: GeoBounds,
            asOf: Instant,
        ): MapObjectAreaResponse {
            fetchAreaRequests += bounds
            return responseOverride ?: MapObjectAreaResponse(
                resourceSpawns = listOf(
                    ResourceSpawn(
                        id = resourceId(bounds),
                        typeId = "scrap",
                        position = bounds.center(),
                        collectionRadiusMeters = 25.0,
                        availableFrom = asOf,
                        availableUntil = asOf.plusSeconds(60),
                    ),
                ),
            )
        }

        override suspend fun fetchActiveResourceSpawn(
            spawnId: String,
            asOf: Instant,
        ): ResourceSpawn? = null

        fun resourceId(bounds: GeoBounds): String =
            "resource:${bounds.west}:${bounds.north}"

        private fun GeoBounds.center(): GeoPoint =
            GeoPoint(
                lat = (south + north) / 2.0,
                lon = (west + east) / 2.0,
            )
    }
}
