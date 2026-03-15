package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.DiscoveredPoiEntity
import com.github.arhor.journey.domain.model.DiscoveredPoi
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
}
