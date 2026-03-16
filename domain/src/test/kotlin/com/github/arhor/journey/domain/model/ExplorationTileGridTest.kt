package com.github.arhor.journey.domain.model

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.Test

class ExplorationTileGridTest {

    @Test
    fun `tileAt should map the equator origin to the expected slippy tile when zoom is 2`() {
        // Given
        val point = GeoPoint(
            lat = 0.0,
            lon = 0.0,
        )

        // When
        val actual = ExplorationTileGrid.tileAt(
            point = point,
            zoom = 2,
        )

        // Then
        actual shouldBe ExplorationTile(
            zoom = 2,
            x = 2,
            y = 2,
        )
    }

    @Test
    fun `tileRange should round-trip exact tile bounds when visible bounds already match canonical tiles`() {
        // Given
        val expectedRange = ExplorationTileRange(
            zoom = 4,
            minX = 8,
            maxX = 9,
            minY = 5,
            maxY = 6,
        )

        // When
        val actual = ExplorationTileGrid.tileRange(
            bounds = ExplorationTileGrid.bounds(expectedRange),
            zoom = expectedRange.zoom,
        )

        // Then
        actual shouldBe expectedRange
    }

    @Test
    fun `bounds should contain the original point when tile is derived from the same location`() {
        // Given
        val point = GeoPoint(
            lat = 37.7749,
            lon = -122.4194,
        )

        // When
        val tile = ExplorationTileGrid.tileAt(
            point = point,
            zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
        )
        val actual = ExplorationTileGrid.bounds(tile)

        // Then
        (point.lat in actual.south..actual.north) shouldBe true
        (point.lon in actual.west..actual.east) shouldBe true
    }

    @Test
    fun `revealTilesAround should include neighboring tiles when reveal bounds cross canonical tile borders`() {
        // Given
        val point = GeoPoint(
            lat = 0.0,
            lon = 0.0,
        )

        // When
        val actual = ExplorationTileGrid.revealTilesAround(
            point = point,
            radiusMeters = 1_000.0,
            zoom = 2,
        )

        // Then
        actual shouldContainExactlyInAnyOrder setOf(
            ExplorationTile(zoom = 2, x = 1, y = 1),
            ExplorationTile(zoom = 2, x = 2, y = 1),
            ExplorationTile(zoom = 2, x = 1, y = 2),
            ExplorationTile(zoom = 2, x = 2, y = 2),
        )
    }
}
