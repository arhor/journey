package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.DiscoveredPoiEntity
import com.github.arhor.journey.data.local.db.entity.ExploredTileEntity
import com.github.arhor.journey.domain.model.DiscoveredPoi
import com.github.arhor.journey.domain.model.MapTile
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant

class ExplorationMapperTest {

    @Test
    fun `toDomain should map instant when discovered poi entity is provided`() {
        // Given
        val discoveredAt = Instant.parse("2026-02-15T12:00:00Z")
        val poiId = 1L
        val entity = DiscoveredPoiEntity(
            poiId = poiId,
            discoveredAt = discoveredAt,
        )

        // When
        val actual = entity.toDomain()

        // Then
        actual shouldBe DiscoveredPoi(
            poiId = poiId,
            discoveredAt = discoveredAt,
        )
    }

    @Test
    fun `toEntity should keep pre epoch instant when discovered poi is before 1970`() {
        // Given
        val poiId = 2L
        val discovered = DiscoveredPoi(
            poiId = poiId,
            discoveredAt = Instant.parse("1969-12-31T23:59:58Z"),
        )

        // When
        val actual = discovered.toEntity()

        // Then
        actual shouldBe DiscoveredPoiEntity(
            poiId = poiId,
            discoveredAt = Instant.parse("1969-12-31T23:59:58Z"),
        )
    }

    @Test
    fun `toDomain should map exploration tile entity coordinates when prototype tile is provided`() {
        // Given
        val entity = ExploredTileEntity(
            zoom = 16,
            x = 34567,
            y = 22345,
        )

        // When
        val actual = entity.toDomain()

        // Then
        actual shouldBe MapTile(
            zoom = 16,
            x = 34567,
            y = 22345,
        )
    }

    @Test
    fun `toEntity should map exploration tile coordinates when prototype tile is provided`() {
        // Given
        val tile = MapTile(
            zoom = 16,
            x = 34567,
            y = 22345,
        )

        // When
        val actual = tile.toEntity()

        // Then
        actual shouldBe ExploredTileEntity(
            zoom = 16,
            x = 34567,
            y = 22345,
        )
    }
}
