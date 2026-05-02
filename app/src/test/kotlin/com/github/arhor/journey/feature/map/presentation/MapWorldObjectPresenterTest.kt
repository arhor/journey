package com.github.arhor.journey.feature.map.presentation

import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.domain.CANONICAL_ZOOM
import com.github.arhor.journey.domain.internal.tileAt
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.Watchtower
import com.github.arhor.journey.domain.model.WatchtowerPhase
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapObjectKind
import com.github.arhor.journey.feature.map.model.WatchtowerMarkerState
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

    @Test
    fun `presentWatchtower should map marker state from watchtower interaction state`() {
        // Given
        val subject = MapWorldObjectPresenter()

        // When
        val dormant = subject.presentWatchtower(
            watchtower(
                id = "dormant",
                phase = WatchtowerPhase.DISCOVERED_DORMANT,
            ),
        )
        val claimable = subject.presentWatchtower(
            watchtower(
                id = "claimable",
                phase = WatchtowerPhase.DISCOVERED_DORMANT,
                canClaim = true,
            ),
        )
        val claimed = subject.presentWatchtower(
            watchtower(
                id = "claimed",
                phase = WatchtowerPhase.CLAIMED,
                level = 3,
                revealRadiusMeters = 100.6,
            ),
        )
        val upgradeAvailable = subject.presentWatchtower(
            watchtower(
                id = "upgrade",
                phase = WatchtowerPhase.CLAIMED,
                canUpgrade = true,
            ),
        )

        // Then
        dormant.watchtowerMarkerState shouldBe WatchtowerMarkerState.DISCOVERED_DORMANT
        dormant.watchtowerLevel shouldBe 1
        claimable.watchtowerMarkerState shouldBe WatchtowerMarkerState.CLAIMABLE
        claimed.watchtowerMarkerState shouldBe WatchtowerMarkerState.CLAIMED
        claimed.watchtowerLevel shouldBe 3
        claimed.radiusMeters shouldBe 101
        upgradeAvailable.watchtowerMarkerState shouldBe WatchtowerMarkerState.UPGRADE_AVAILABLE
    }

    private fun watchtower(
        id: String,
        phase: WatchtowerPhase,
        canClaim: Boolean = false,
        canUpgrade: Boolean = false,
        level: Int? = null,
        revealRadiusMeters: Double? = null,
    ): Watchtower =
        Watchtower(
            id = id,
            name = "Watchtower $id",
            description = "Description $id",
            location = GeoPoint(lat = 50.45, lon = 30.52),
            interactionRadiusMeters = 25.0,
            phase = phase,
            level = level,
            revealRadiusMeters = revealRadiusMeters,
            claimCost = null,
            nextUpgradeCost = null,
            nextRevealRadiusMeters = null,
            canClaim = canClaim,
            canUpgrade = canUpgrade,
            distanceMeters = null,
        )
}
