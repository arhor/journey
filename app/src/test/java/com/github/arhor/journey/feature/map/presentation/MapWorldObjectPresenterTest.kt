package com.github.arhor.journey.feature.map.presentation

import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.domain.CANONICAL_ZOOM
import com.github.arhor.journey.domain.internal.tileAt
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapObjectKind
import io.kotest.matchers.shouldBe
import org.junit.Test

class MapWorldObjectPresenterTest {

    @Test
    fun `presentResourceSpawns should map resource spawn with fog-hidden state`() {
        // Given
        val subject = MapWorldObjectPresenter()
        val spawn = ResourceSpawn(
            id = "cell-1-slot-0",
            typeId = ResourceType.SCRAP.typeId,
            position = GeoPoint(lat = 50.46, lon = 30.53),
            collectionRadiusMeters = 24.9,
        )
        val visibleTile = tileAt(point = spawn.position, zoom = CANONICAL_ZOOM)

        // When
        val visible = subject.presentResourceSpawns(
            resourceSpawns = listOf(spawn),
            canonicalZoom = CANONICAL_ZOOM,
            visibilityTileMask = setOf(visibleTile),
        ).single()
        val hidden = subject.presentResourceSpawns(
            resourceSpawns = listOf(spawn),
            canonicalZoom = CANONICAL_ZOOM,
            visibilityTileMask = emptySet(),
        ).single()

        // Then
        visible.id shouldBe "spawn:cell-1-slot-0"
        visible.kind shouldBe MapObjectKind.ResourceSpawn
        visible.title shouldBe "Scrap"
        visible.description shouldBe null
        visible.position shouldBe LatLng(latitude = 50.46, longitude = 30.53)
        visible.radiusMeters shouldBe 24
        visible.isDiscovered shouldBe false
        visible.isHiddenByFog shouldBe false
        visible.resourceType shouldBe ResourceType.SCRAP
        hidden.isHiddenByFog shouldBe true
    }
}
