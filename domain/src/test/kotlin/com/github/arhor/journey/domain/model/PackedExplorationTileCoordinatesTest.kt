package com.github.arhor.journey.domain.model

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.Test

class PackedExplorationTileCoordinatesTest {

    @Test
    fun `pack should round trip normal values`() {
        // Given
        val zoom = 16
        val x = 34567
        val y = 22345

        // When
        val actual = PackedExplorationTileCoordinates.pack(
            zoom = zoom,
            x = x,
            y = y,
        )

        // Then
        PackedExplorationTileCoordinates.unpackZoom(actual) shouldBe zoom
        PackedExplorationTileCoordinates.unpackX(actual) shouldBe x
        PackedExplorationTileCoordinates.unpackY(actual) shouldBe y
    }

    @Test
    fun `pack should mask zoom to 8 bits`() {
        // Given
        val zoom = 0x1AB

        // When
        val actual = PackedExplorationTileCoordinates.pack(
            zoom = zoom,
            x = 1,
            y = 2,
        )

        // Then
        PackedExplorationTileCoordinates.unpackZoom(actual) shouldBe 0xAB
    }

    @Test
    fun `pack should keep 24 bit axis boundary values reversible`() {
        // Given
        val x = PackedExplorationTileCoordinates.MAX_AXIS_COORDINATE
        val y = PackedExplorationTileCoordinates.MAX_AXIS_COORDINATE

        // When
        val actual = PackedExplorationTileCoordinates.pack(
            zoom = PackedExplorationTileCoordinates.MAX_ZOOM,
            x = x,
            y = y,
        )

        // Then
        PackedExplorationTileCoordinates.unpackX(actual) shouldBe x
        PackedExplorationTileCoordinates.unpackY(actual) shouldBe y
    }

    @Test
    fun `pack should match expected exact long for representative values`() {
        // Given
        val zoom = 0xAB
        val x = 0x123456
        val y = 0x654321

        // When
        val actual = PackedExplorationTileCoordinates.pack(
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
            ExplorationTile(zoom = 0, x = 0, y = 0),
            ExplorationTile(zoom = 16, x = 34567, y = 22345),
            ExplorationTile(zoom = 16, x = 34567, y = 22346),
            ExplorationTile(zoom = 16, x = 34568, y = 22345),
            ExplorationTile(zoom = 17, x = 34567, y = 22345),
            ExplorationTile(zoom = PackedExplorationTileCoordinates.MAX_ZOOM, x = 1, y = 1),
        )

        // When
        val packed = tiles.map(PackedExplorationTileCoordinates::pack)

        // Then
        packed.distinct().shouldHaveSize(tiles.size)
    }
}
