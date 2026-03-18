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
    fun `playerLightContributionsAt should assign expected light values across tile rings`() {
        // Given
        val point = GeoPoint(
            lat = 0.0,
            lon = 0.0,
        )

        // When
        val actual = ExplorationTileGrid.playerLightContributionsAt(
            point = point,
            zoom = 3,
        )
        val actualByTile = actual.associateBy(ExplorationTileLight::tile)

        // Then
        actualByTile[ExplorationTile(zoom = 3, x = 4, y = 4)]?.light shouldBe 1.0f
        actualByTile[ExplorationTile(zoom = 3, x = 3, y = 4)]?.light shouldBe 0.66f
        actualByTile[ExplorationTile(zoom = 3, x = 6, y = 4)]?.light shouldBe 0.33f
    }

    @Test
    fun `playerLightContributionsAt should not emit tiles outside the supported ring radius`() {
        // Given
        val point = GeoPoint(
            lat = 0.0,
            lon = 0.0,
        )

        // When
        val actual = ExplorationTileGrid.playerLightContributionsAt(
            point = point,
            zoom = 3,
        )

        // Then
        actual.map(ExplorationTileLight::tile) shouldContainExactlyInAnyOrder setOf(
            ExplorationTile(zoom = 3, x = 2, y = 2),
            ExplorationTile(zoom = 3, x = 3, y = 2),
            ExplorationTile(zoom = 3, x = 4, y = 2),
            ExplorationTile(zoom = 3, x = 5, y = 2),
            ExplorationTile(zoom = 3, x = 6, y = 2),
            ExplorationTile(zoom = 3, x = 2, y = 3),
            ExplorationTile(zoom = 3, x = 3, y = 3),
            ExplorationTile(zoom = 3, x = 4, y = 3),
            ExplorationTile(zoom = 3, x = 5, y = 3),
            ExplorationTile(zoom = 3, x = 6, y = 3),
            ExplorationTile(zoom = 3, x = 2, y = 4),
            ExplorationTile(zoom = 3, x = 3, y = 4),
            ExplorationTile(zoom = 3, x = 4, y = 4),
            ExplorationTile(zoom = 3, x = 5, y = 4),
            ExplorationTile(zoom = 3, x = 6, y = 4),
            ExplorationTile(zoom = 3, x = 2, y = 5),
            ExplorationTile(zoom = 3, x = 3, y = 5),
            ExplorationTile(zoom = 3, x = 4, y = 5),
            ExplorationTile(zoom = 3, x = 5, y = 5),
            ExplorationTile(zoom = 3, x = 6, y = 5),
            ExplorationTile(zoom = 3, x = 2, y = 6),
            ExplorationTile(zoom = 3, x = 3, y = 6),
            ExplorationTile(zoom = 3, x = 4, y = 6),
            ExplorationTile(zoom = 3, x = 5, y = 6),
            ExplorationTile(zoom = 3, x = 6, y = 6),
        )
    }

    @Test
    fun `playerLightContributionsAt should clamp light rings to world bounds near the tile grid edge`() {
        // Given
        val point = GeoPoint(
            lat = 85.0,
            lon = -179.999,
        )

        // When
        val actual = ExplorationTileGrid.playerLightContributionsAt(
            point = point,
            zoom = 2,
        )

        // Then
        actual.map(ExplorationTileLight::tile) shouldContainExactlyInAnyOrder setOf(
            ExplorationTile(zoom = 2, x = 0, y = 0),
            ExplorationTile(zoom = 2, x = 1, y = 0),
            ExplorationTile(zoom = 2, x = 0, y = 1),
            ExplorationTile(zoom = 2, x = 1, y = 1),
            ExplorationTile(zoom = 2, x = 2, y = 0),
            ExplorationTile(zoom = 2, x = 2, y = 1),
            ExplorationTile(zoom = 2, x = 0, y = 2),
            ExplorationTile(zoom = 2, x = 1, y = 2),
            ExplorationTile(zoom = 2, x = 2, y = 2),
        )
    }
}
