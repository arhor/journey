package com.github.arhor.journey.feature.map

import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileRange
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test

class FogOfWarCalculatorTest {

    @Test
    fun `calculateFogOfWarBands should emit a full fog band when every tile is unlit`() {
        // Given
        val tileRange = ExplorationTileRange(
            zoom = 16,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )

        // When
        val actual = calculateFogOfWarBands(
            tileRange = tileRange,
            tileLightByTile = emptyMap(),
        )

        // Then
        actual shouldContainExactly listOf(
            FogOfWarBandUiState(
                opacity = fogOpacityForTest(0.0f),
                ranges = listOf(
                    ExplorationTileRange(
                        zoom = 16,
                        minX = 10,
                        maxX = 11,
                        minY = 20,
                        maxY = 21,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `calculateFogOfWarBands should exclude brighter tiles from darker fog bands`() {
        // Given
        val tileRange = ExplorationTileRange(
            zoom = 16,
            minX = 10,
            maxX = 13,
            minY = 20,
            maxY = 20,
        )

        // When
        val actual = calculateFogOfWarBands(
            tileRange = tileRange,
            tileLightByTile = mapOf(
                ExplorationTile(zoom = 16, x = 10, y = 20) to 1.0f,
                ExplorationTile(zoom = 16, x = 11, y = 20) to 0.66f,
                ExplorationTile(zoom = 16, x = 12, y = 20) to 0.33f,
            ),
        )

        // Then
        actual shouldContainExactly listOf(
            FogOfWarBandUiState(
                opacity = fogOpacityForTest(0.0f),
                ranges = listOf(
                    ExplorationTileRange(
                        zoom = 16,
                        minX = 13,
                        maxX = 13,
                        minY = 20,
                        maxY = 20,
                    ),
                ),
            ),
            FogOfWarBandUiState(
                opacity = fogOpacityForTest(0.33f),
                ranges = listOf(
                    ExplorationTileRange(
                        zoom = 16,
                        minX = 12,
                        maxX = 12,
                        minY = 20,
                        maxY = 20,
                    ),
                ),
            ),
            FogOfWarBandUiState(
                opacity = fogOpacityForTest(0.66f),
                ranges = listOf(
                    ExplorationTileRange(
                        zoom = 16,
                        minX = 11,
                        maxX = 11,
                        minY = 20,
                        maxY = 20,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `calculateFogOfWarBands should merge vertical runs when adjacent rows share the same fog opacity span`() {
        // Given
        val tileRange = ExplorationTileRange(
            zoom = 16,
            minX = 0,
            maxX = 2,
            minY = 0,
            maxY = 2,
        )

        // When
        val actual = calculateFogOfWarBands(
            tileRange = tileRange,
            tileLightByTile = mapOf(
                ExplorationTile(zoom = 16, x = 1, y = 2) to 1.0f,
            ),
        )

        // Then
        actual shouldContainExactly listOf(
            FogOfWarBandUiState(
                opacity = fogOpacityForTest(0.0f),
                ranges = listOf(
                    ExplorationTileRange(zoom = 16, minX = 0, maxX = 2, minY = 0, maxY = 1),
                    ExplorationTileRange(zoom = 16, minX = 0, maxX = 0, minY = 2, maxY = 2),
                    ExplorationTileRange(zoom = 16, minX = 2, maxX = 2, minY = 2, maxY = 2),
                ),
            ),
        )
    }

    @Test
    fun `calculateFogOfWarBands should return empty when every visible tile is fully lit`() {
        // Given
        val tileRange = ExplorationTileRange(
            zoom = 16,
            minX = 10,
            maxX = 11,
            minY = 20,
            maxY = 21,
        )

        // When
        val actual = calculateFogOfWarBands(
            tileRange = tileRange,
            tileLightByTile = tileRange.asSequence()
                .associateWith { 1.0f },
        )

        // Then
        actual.isEmpty() shouldBe true
    }
}

private fun fogOpacityForTest(light: Float): Float = 0.90f * (1.0f - light)
