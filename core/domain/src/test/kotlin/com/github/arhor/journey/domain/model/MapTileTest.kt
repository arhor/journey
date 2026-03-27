package com.github.arhor.journey.domain.model

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.Test

class MapTileTest {

    @Test
    fun `pack should round trip normal values`() {
        // Given
        val zoom = 16
        val x = 34567
        val y = 22345

        // When
        val actual = MapTile.pack(
            zoom = zoom,
            x = x,
            y = y,
        )

        // Then
        MapTile.unpackZoom(actual) shouldBe zoom
        MapTile.unpackX(actual) shouldBe x
        MapTile.unpackY(actual) shouldBe y
    }

    @Test
    fun `pack should keep 24 bit axis boundary values reversible`() {
        // Given
        val x = MAX_AXIS_COORDINATE
        val y = MAX_AXIS_COORDINATE

        // When
        val actual = MapTile.pack(
            zoom = MAX_ZOOM,
            x = x,
            y = y,
        )

        // Then
        MapTile.unpackX(actual) shouldBe x
        MapTile.unpackY(actual) shouldBe y
    }

    @Test
    fun `pack should match expected exact long for representative values`() {
        // Given
        val zoom = 0xAB
        val x = 0x123456
        val y = 0x654321

        // When
        val actual = MapTile.pack(
            zoom = zoom,
            x = x,
            y = y,
        )

        // Then
        actual shouldBe 0x00AB123456654321L
    }

    @Test
    fun `pack should produce unique values for representative coordinate combinations`() {
        // Given
        val tiles = listOf(
            MapTile(zoom = 0, x = 0, y = 0),
            MapTile(zoom = 16, x = 34567, y = 22345),
            MapTile(zoom = 16, x = 34567, y = 22346),
            MapTile(zoom = 16, x = 34568, y = 22345),
            MapTile(zoom = 17, x = 34567, y = 22345),
            MapTile(zoom = MAX_ZOOM, x = 1, y = 1),
        )

        // When
        val packed = tiles.map { it.packedValue }

        // Then
        packed.distinct().shouldHaveSize(tiles.size)
    }

    companion object {
        const val MAX_ZOOM: Int = 0xFF
        const val MAX_AXIS_COORDINATE: Int = 0xFFFFFF
    }
}
