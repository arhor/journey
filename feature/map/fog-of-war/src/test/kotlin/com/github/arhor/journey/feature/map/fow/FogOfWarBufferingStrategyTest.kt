package com.github.arhor.journey.feature.map.fow

import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.feature.map.fow.model.FogBufferRegion
import com.github.arhor.journey.feature.map.fow.shouldRecompute
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.Test

class FogOfWarBufferingStrategyTest {

    @Test
    fun `buffer region should keep trigger bounds nested inside buffered bounds`() {
        // Given
        val visibleTileRange = ExplorationTileRange(
            zoom = 17,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )

        // When
        val actual = createFogBufferRegion(visibleTileRange)

        // Then
        actual.bufferedTileRange.contains(actual.triggerTileRange) shouldBe true
        actual.bufferedBounds.strictlyContains(actual.triggerBounds) shouldBe true
    }

    @Test
    fun `buffer region should not request recompute while viewport remains fully inside trigger bounds`() {
        // Given
        val visibleBounds = GeoBounds(
            south = 50.0,
            west = 30.0,
            north = 50.01,
            east = 30.01,
        )
        val bufferRegion = createFogBufferRegion(
            visibleBounds = visibleBounds,
            canonicalZoom = 17,
        )

        // When
        val actual = bufferRegion.shouldRecompute(visibleBounds)

        // Then
        actual shouldBe false
    }

    @Test
    fun `buffer region should request recompute when viewport touches trigger bounds`() {
        // Given
        val triggerBounds = GeoBounds(
            south = 50.0,
            west = 30.0,
            north = 50.1,
            east = 30.1,
        )
        val bufferRegion = FogBufferRegion(
            triggerBounds = triggerBounds,
            bufferedBounds = GeoBounds(
                south = 49.9,
                west = 29.9,
                north = 50.2,
                east = 30.2,
            ),
            triggerTileRange = ExplorationTileRange(
                zoom = 17,
                minX = 10,
                maxX = 12,
                minY = 20,
                maxY = 22,
            ),
            bufferedTileRange = ExplorationTileRange(
                zoom = 17,
                minX = 9,
                maxX = 13,
                minY = 19,
                maxY = 23,
            ),
        )
        val touchingViewport = GeoBounds(
            south = 50.02,
            west = 30.02,
            north = 50.08,
            east = triggerBounds.east,
        )

        // When
        val actual = bufferRegion.shouldRecompute(touchingViewport)

        // Then
        actual shouldBe true
    }

    @Test
    fun `buffer region should keep at least two tile lead beyond trigger on each side`() {
        // Given
        val visibleTileRange = ExplorationTileRange(
            zoom = 17,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )

        // When
        val actual = createFogBufferRegion(visibleTileRange)

        // Then
        (actual.triggerTileRange.minX - actual.bufferedTileRange.minX).shouldBeGreaterThanOrEqual(2)
        (actual.bufferedTileRange.maxX - actual.triggerTileRange.maxX).shouldBeGreaterThanOrEqual(2)
        (actual.triggerTileRange.minY - actual.bufferedTileRange.minY).shouldBeGreaterThanOrEqual(2)
        (actual.bufferedTileRange.maxY - actual.triggerTileRange.maxY).shouldBeGreaterThanOrEqual(2)
    }

    @Test
    fun `buffer region should target around five screens for trigger and ten for buffered area`() {
        // Given
        val visibleTileRange = ExplorationTileRange(
            zoom = 17,
            minX = 100,
            maxX = 119,
            minY = 200,
            maxY = 219,
        )

        // When
        val actual = createFogBufferRegion(visibleTileRange)
        val triggerAreaRatio = actual.triggerTileRange.areaRatioTo(visibleTileRange)
        val bufferedAreaRatio = actual.bufferedTileRange.areaRatioTo(visibleTileRange)

        // Then
        (triggerAreaRatio in 4.5..5.8) shouldBe true
        (bufferedAreaRatio in 9.0..11.2) shouldBe true
    }

    private fun ExplorationTileRange.contains(other: ExplorationTileRange): Boolean {
        return zoom == other.zoom &&
            other.minX >= minX &&
            other.maxX <= maxX &&
            other.minY >= minY &&
            other.maxY <= maxY
    }

    private fun ExplorationTileRange.areaRatioTo(other: ExplorationTileRange): Double {
        val thisArea = (maxX - minX + 1).toLong() * (maxY - minY + 1).toLong()
        val otherArea = (other.maxX - other.minX + 1).toLong() * (other.maxY - other.minY + 1).toLong()
        return thisArea.toDouble() / otherArea.toDouble()
    }

    internal fun createFogBufferRegion(
        visibleBounds: GeoBounds,
        canonicalZoom: Int,
    ): FogBufferRegion = createFogBufferRegion(
        visibleTileRange = createFogViewportSnapshot(
            visibleBounds = visibleBounds,
            canonicalZoom = canonicalZoom,
        ).visibleTileRange,
    )
}
