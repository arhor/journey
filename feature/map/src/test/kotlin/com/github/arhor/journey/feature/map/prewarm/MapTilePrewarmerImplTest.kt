package com.github.arhor.journey.feature.map.prewarm

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapViewportSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class MapTilePrewarmerImplTest {

    @Test
    fun `prewarm should pass fetched cache metadata through to the cache writer`() = runTest {
        // Given
        val writer = RecordingMapTileCacheWriter()
        val fetcher = RecordingMapTileResourceFetcher(
            responses = mapOf(
                EXPECTED_TILE_URL to FetchedMapResource(
                    url = EXPECTED_TILE_URL,
                    body = "tile".encodeToByteArray(),
                    modifiedEpochSeconds = 10,
                    expiresEpochSeconds = 20,
                    etag = "etag-1",
                    mustRevalidate = true,
                ),
            ),
        )
        val prewarmer = createPrewarmer(
            scope = this,
            fetcher = fetcher,
            writer = writer,
        )

        // When
        prewarmer.prewarm(prewarmRequest(targetLongitude = 0.0))
        advanceUntilIdle()

        // Then
        writer.resources.shouldHaveSize(1)
        val cachedResource = writer.resources.single()
        cachedResource.url shouldBe EXPECTED_TILE_URL
        cachedResource.body.contentEquals("tile".encodeToByteArray()) shouldBe true
        cachedResource.modifiedEpochSeconds shouldBe 10
        cachedResource.expiresEpochSeconds shouldBe 20
        cachedResource.etag shouldBe "etag-1"
        cachedResource.mustRevalidate shouldBe true
    }

    @Test
    fun `prewarm should cancel the previous job when a new request arrives with the same key`() = runTest {
        // Given
        val writer = RecordingMapTileCacheWriter()
        val fetcher = RecordingMapTileResourceFetcher(
            delayMillis = 100,
            responses = mapOf(
                EXPECTED_TILE_URL to FetchedMapResource(
                    url = EXPECTED_TILE_URL,
                    body = "first".encodeToByteArray(),
                ),
                "https://tiles.example.com/2/2/1.mvt" to FetchedMapResource(
                    url = "https://tiles.example.com/2/2/1.mvt",
                    body = "second".encodeToByteArray(),
                ),
                "https://tiles.example.com/2/2/2.mvt" to FetchedMapResource(
                    url = "https://tiles.example.com/2/2/2.mvt",
                    body = "second".encodeToByteArray(),
                ),
                "https://tiles.example.com/2/3/1.mvt" to FetchedMapResource(
                    url = "https://tiles.example.com/2/3/1.mvt",
                    body = "second".encodeToByteArray(),
                ),
                "https://tiles.example.com/2/3/2.mvt" to FetchedMapResource(
                    url = "https://tiles.example.com/2/3/2.mvt",
                    body = "second".encodeToByteArray(),
                ),
            ),
        )
        val prewarmer = createPrewarmer(
            scope = this,
            fetcher = fetcher,
            writer = writer,
        )

        // When
        val firstJob = prewarmer.prewarm(prewarmRequest(targetLongitude = 0.0))
        advanceTimeBy(10)
        val secondJob = prewarmer.prewarm(prewarmRequest(targetLongitude = 90.0))
        advanceUntilIdle()

        // Then
        firstJob.isCancelled shouldBe true
        secondJob.isCancelled shouldBe false
        writer.resources.map(FetchedMapResource::url).toSet() shouldBe setOf(
            "https://tiles.example.com/2/2/1.mvt",
            "https://tiles.example.com/2/2/2.mvt",
            "https://tiles.example.com/2/3/1.mvt",
            "https://tiles.example.com/2/3/2.mvt",
        )
    }

    private fun createPrewarmer(
        scope: TestScope,
        fetcher: MapTileResourceFetcher,
        writer: MapTileCacheWriter,
    ): MapTilePrewarmerImpl {
        val context = mockk<Context>()
        val resources = mockk<Resources>()
        val displayMetrics = DisplayMetrics().apply {
            density = 1f
        }
        every { context.resources } returns resources
        every { resources.displayMetrics } returns displayMetrics

        return MapTilePrewarmerImpl(
            context = context,
            appScope = scope,
            styleResolver = MapTileStyleResolver(
                json = Json {
                    ignoreUnknownKeys = true
                },
            ),
            coverageCalculator = MapTileCoverageCalculator(),
            resourceFetcher = fetcher,
            cacheWriter = writer,
        )
    }

    private fun prewarmRequest(targetLongitude: Double): MapTilePrewarmRequest =
        MapTilePrewarmRequest(
            requestKey = "map",
            style = MapStyle.bundle(
                id = "bundle-style",
                name = "Bundle",
                value = """
                    {
                      "version": 8,
                      "sources": {
                        "main": {
                          "type": "vector",
                          "tiles": ["https://tiles.example.com/{z}/{x}/{y}.mvt"],
                          "minzoom": 2,
                          "maxzoom": 2
                        }
                      },
                      "layers": []
                    }
                """.trimIndent(),
            ),
            currentCamera = CameraPositionState(
                target = LatLng(latitude = 0.0, longitude = 0.0),
                zoom = 2.0,
            ),
            targetCamera = CameraPositionState(
                target = LatLng(latitude = 0.0, longitude = targetLongitude),
                zoom = 2.0,
            ),
            currentVisibleBounds = GeoBounds(
                south = -10.0,
                west = -10.0,
                north = 10.0,
                east = 10.0,
            ),
            viewportSize = MapViewportSize(widthPx = 1080, heightPx = 1920),
            animationDuration = kotlin.time.Duration.ZERO,
            sampleCount = 1,
            burstLimit = 8,
        )

    private companion object {
        private const val EXPECTED_TILE_URL = "https://tiles.example.com/2/1/1.mvt"
    }
}

private class RecordingMapTileResourceFetcher(
    private val responses: Map<String, FetchedMapResource>,
    private val delayMillis: Long = 0,
) : MapTileResourceFetcher {
    override suspend fun fetch(url: String): FetchedMapResource? {
        if (delayMillis > 0) {
            delay(delayMillis)
        }
        return responses[url]
    }
}

private class RecordingMapTileCacheWriter : MapTileCacheWriter {
    val resources = mutableListOf<FetchedMapResource>()

    override suspend fun cache(resource: FetchedMapResource) {
        resources += resource
    }
}
