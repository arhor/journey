package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.DiscoveredPoiEntity
import com.github.arhor.journey.data.local.db.entity.ExplorationTileEntity
import com.github.arhor.journey.domain.model.DiscoveredPoi
import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileLight
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant

class ExplorationMapperTest {

    @Test
    fun `toDomain should map epoch millis to instant when discovered poi entity is provided`() {
        // Given
        val discoveredAt = Instant.parse("2026-02-15T12:00:00Z")
        val entity = DiscoveredPoiEntity(
            poiId = "poi-a",
            discoveredAtMs = discoveredAt.toEpochMilli(),
        )

        // When
        val actual = entity.toDomain()

        // Then
        actual shouldBe DiscoveredPoi(
            poiId = "poi-a",
            discoveredAt = discoveredAt,
        )
    }

    @Test
    fun `toEntity should keep pre epoch timestamp when discovered poi is before 1970`() {
        // Given
        val discovered = DiscoveredPoi(
            poiId = "poi-b",
            discoveredAt = Instant.parse("1969-12-31T23:59:58Z"),
        )

        // When
        val actual = discovered.toEntity()

        // Then
        actual shouldBe DiscoveredPoiEntity(
            poiId = "poi-b",
            discoveredAtMs = Instant.parse("1969-12-31T23:59:58Z").toEpochMilli(),
        )
    }

    @Test
    fun `toDomain should map exploration tile entity coordinates when prototype tile is provided`() {
        // Given
        val entity = ExplorationTileEntity(
            zoom = 16,
            x = 34567,
            y = 22345,
            light = 0.66f,
        )

        // When
        val actual = entity.toDomain()

        // Then
        actual shouldBe ExplorationTileLight(
            tile = ExplorationTile(
                zoom = 16,
                x = 34567,
                y = 22345,
            ),
            light = 0.66f,
        )
    }

    @Test
    fun `toEntity should map exploration tile coordinates when prototype tile is provided`() {
        // Given
        val tileLight = ExplorationTileLight(
            tile = ExplorationTile(
                zoom = 16,
                x = 34567,
                y = 22345,
            ),
            light = 0.33f,
        )

        // When
        val actual = tileLight.toEntity()

        // Then
        actual shouldBe ExplorationTileEntity(
            zoom = 16,
            x = 34567,
            y = 22345,
            light = 0.33f,
        )
    }
}
