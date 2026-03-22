package com.github.arhor.journey.feature.map.renderer

import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.feature.map.model.FogOfWarCacheMetrics
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.util.concurrent.CancellationException

class FogOfWarRenderDataFactoryTest {

    @Test
    fun `create should return null when fog ranges are empty`() {
        // Given
        val factory = FogOfWarRenderDataFactory()

        // When
        val actual = factory.create(emptyList())

        // Then
        actual shouldBe null
    }

    @Test
    fun `create should reuse cached render data for equivalent fog inputs`() {
        // Given
        val factory = FogOfWarRenderDataFactory()
        val fogRanges = listOf(
            ExplorationTileRange(
                zoom = 16,
                minX = 34567,
                maxX = 34568,
                minY = 22345,
                maxY = 22345,
            ),
            ExplorationTileRange(
                zoom = 16,
                minX = 34567,
                maxX = 34567,
                minY = 22346,
                maxY = 22346,
            ),
        )

        // When
        val first = factory.create(fogRanges)
        val second = factory.create(fogRanges.asReversed())

        // Then
        first.shouldNotBeNull()
        second.shouldNotBeNull()
        (first === second) shouldBe true
    }

    @Test
    fun `createDetailed should expose geometry metrics and cache hits for equivalent fog inputs`() {
        // Given
        val factory = FogOfWarRenderDataFactory()
        val fogRanges = listOf(
            ExplorationTileRange(
                zoom = 16,
                minX = 34567,
                maxX = 34568,
                minY = 22345,
                maxY = 22345,
            ),
            ExplorationTileRange(
                zoom = 16,
                minX = 34567,
                maxX = 34567,
                minY = 22346,
                maxY = 22346,
            ),
        )

        // When
        val first = factory.createDetailed(fogRanges)
        val second = factory.createDetailed(fogRanges.asReversed())

        // Then
        first.shouldNotBeNull()
        second.shouldNotBeNull()
        first.metrics.cacheHit shouldBe false
        first.metrics.expandedFogCellCount shouldBe 3L
        first.metrics.connectedRegionCount shouldBe 1
        first.metrics.featureCount shouldBe 1
        second.metrics.cacheHit shouldBe true
        factory.cacheMetricsSnapshot() shouldBe FogOfWarCacheMetrics(
            renderHits = 1,
            renderMisses = 1,
            fullRangeHits = 0,
            fullRangeMisses = 0,
        )
    }

    @Test
    fun `create should stop geometry preparation when cancellation is requested`() {
        // Given
        val factory = FogOfWarRenderDataFactory()
        val fogRanges = listOf(
            ExplorationTileRange(
                zoom = 16,
                minX = 34567,
                maxX = 34570,
                minY = 22345,
                maxY = 22348,
            ),
        )
        var cancellationChecks = 0

        // When
        val result = runCatching {
            factory.create(fogRanges) {
                cancellationChecks += 1
                if (cancellationChecks >= 2) {
                    throw CancellationException("cancelled")
                }
            }
        }

        // Then
        (result.exceptionOrNull() is CancellationException) shouldBe true
        (cancellationChecks >= 2) shouldBe true
    }

    @Test
    fun `toFeatureCollection should emit rounded merged polygons for connected fog tiles`() {
        // Given
        val fogRanges = listOf(
            ExplorationTileRange(
                zoom = 16,
                minX = 34567,
                maxX = 34568,
                minY = 22345,
                maxY = 22345,
            ),
            ExplorationTileRange(
                zoom = 16,
                minX = 34567,
                maxX = 34567,
                minY = 22346,
                maxY = 22346,
            ),
        )

        // When
        val actual = fogRanges.toFeatureCollection()

        // Then
        (actual.features.single().geometry.coordinates.single().size > 5) shouldBe true
    }
}
