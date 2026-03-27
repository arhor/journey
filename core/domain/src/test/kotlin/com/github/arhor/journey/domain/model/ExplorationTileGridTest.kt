package com.github.arhor.journey.domain.model

import com.github.arhor.journey.domain.CANONICAL_ZOOM
import com.github.arhor.journey.domain.internal.bounds
import com.github.arhor.journey.domain.internal.latDistanceMeters
import com.github.arhor.journey.domain.internal.lonDistanceMeters
import com.github.arhor.journey.domain.internal.revealTilesAround
import com.github.arhor.journey.domain.internal.tileAt
import com.github.arhor.journey.domain.internal.tileRange
import com.github.arhor.journey.domain.model.copy
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.Test
import kotlin.math.hypot

class ExplorationTileGridTest {

    @Test
    fun `tileAt should map the equator origin to the expected slippy tile when zoom is 2`() {
        // Given
        val point = GeoPoint(
            lat = 0.0,
            lon = 0.0,
        )

        // When
        val actual = tileAt(
            point = point,
            zoom = 2,
        )

        // Then
        actual shouldBe MapTile(
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
        val actual = tileRange(
            bounds = bounds(expectedRange),
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
        val tile = tileAt(
            point = point,
            zoom = CANONICAL_ZOOM,
        )
        val actual = bounds(tile)

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
        val actual = revealTilesAround(
            point = point,
            radiusMeters = 1_000.0,
            zoom = 2,
        )

        // Then
        actual shouldContainExactlyInAnyOrder setOf(
            MapTile(zoom = 2, x = 1, y = 1),
            MapTile(zoom = 2, x = 2, y = 1),
            MapTile(zoom = 2, x = 1, y = 2),
            MapTile(zoom = 2, x = 2, y = 2),
        )
    }

    @Test
    fun `revealTilesAround should exclude diagonal tiles outside the circular radius`() {
        // Given
        val centerTile = tileAt(
            point = GeoPoint(
                lat = 0.001,
                lon = 0.001,
            ),
            zoom = CANONICAL_ZOOM,
        )
        val centerTileBounds = bounds(centerTile)
        val point = GeoPoint(
            lat = (centerTileBounds.south + centerTileBounds.north) / 2.0,
            lon = (centerTileBounds.west + centerTileBounds.east) / 2.0,
        )
        val halfTileWidthMeters = lonDistanceMeters(
            point = point,
            lon = centerTileBounds.east,
        )
        val halfTileHeightMeters = latDistanceMeters(
            point = point,
            latitude = centerTileBounds.north,
        )
        val radiusMeters = maxOf(halfTileWidthMeters, halfTileHeightMeters) + 0.1

        (radiusMeters < hypot(halfTileWidthMeters, halfTileHeightMeters)) shouldBe true

        // When
        val actual = revealTilesAround(
            point = point,
            radiusMeters = radiusMeters,
            zoom = centerTile.zoom,
        )

        // Then
        actual shouldContainExactlyInAnyOrder setOf(
            centerTile,
            centerTile.copy(x = centerTile.x - 1),
            centerTile.copy(x = centerTile.x + 1),
            centerTile.copy(y = centerTile.y - 1),
            centerTile.copy(y = centerTile.y + 1),
        )
    }
}
