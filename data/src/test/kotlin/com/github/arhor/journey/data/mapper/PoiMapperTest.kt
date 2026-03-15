package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.PoiEntity
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.PoiCategory
import com.github.arhor.journey.domain.model.PointOfInterest
import io.kotest.matchers.shouldBe
import org.junit.Test

class PoiMapperTest {

    @Test
    fun `toDomain should fallback to landmark when entity contains unknown category`() {
        // Given
        val entity = PoiEntity(
            id = "poi-1",
            name = "Hidden Place",
            description = "Unknown category value",
            category = "UNKNOWN_CATEGORY",
            lat = 52.2,
            lon = 21.0,
            radiusMeters = 75,
        )

        // When
        val actual = entity.toDomain()

        // Then
        actual.id shouldBe "poi-1"
        actual.category shouldBe PoiCategory.LANDMARK
        actual.location shouldBe GeoPoint(lat = 52.2, lon = 21.0)
    }

    @Test
    fun `toEntity should map all point of interest fields when domain model is provided`() {
        // Given
        val point = PointOfInterest(
            id = "poi-2",
            name = "Stone Gate",
            description = "A mapped landmark",
            category = PoiCategory.SHRINE,
            location = GeoPoint(lat = 51.5, lon = 19.4),
            radiusMeters = 120,
        )

        // When
        val actual = point.toEntity()

        // Then
        actual shouldBe PoiEntity(
            id = "poi-2",
            name = "Stone Gate",
            description = "A mapped landmark",
            category = PoiCategory.SHRINE.name,
            lat = 51.5,
            lon = 19.4,
            radiusMeters = 120,
        )
    }
}
