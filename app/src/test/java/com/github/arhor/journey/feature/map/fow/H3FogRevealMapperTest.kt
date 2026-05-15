package com.github.arhor.journey.feature.map.fow

import com.github.arhor.journey.core.testing.FakeH3Grid
import com.github.arhor.journey.core.testing.hexAround
import com.github.arhor.journey.domain.CANONICAL_ZOOM
import com.github.arhor.journey.domain.internal.tileAt
import com.github.arhor.journey.domain.model.GeoPoint
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.Test

class H3FogRevealMapperTest {

    @Test
    fun `revealTilesForCells should return canonical fog tiles when h3 cell overlaps the map`() {
        // Given
        val cellId = "cell-1"
        val center = GeoPoint(lat = 50.4500, lon = 30.5200)
        val subject = H3FogRevealMapper(
            h3Grid = FakeH3Grid(
                boundaries = mapOf(cellId to hexAround(center)),
            ),
        )

        // When
        val actual = subject.revealTilesForCells(
            h3CellIds = setOf(cellId),
            canonicalZoom = CANONICAL_ZOOM,
        )

        // Then
        actual.shouldNotBeEmpty()
        actual shouldContain tileAt(center, CANONICAL_ZOOM)
    }
}
