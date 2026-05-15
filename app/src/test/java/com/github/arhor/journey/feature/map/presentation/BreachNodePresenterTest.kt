package com.github.arhor.journey.feature.map.presentation

import com.github.arhor.journey.domain.model.BreachNode
import com.github.arhor.journey.domain.model.BreachNodeDefinition
import com.github.arhor.journey.domain.model.BreachNodePhase
import com.github.arhor.journey.domain.model.BreachNodeState
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.feature.map.model.BreachMarkerState
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapObjectKind
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant

class BreachNodePresenterTest {

    private val subject = BreachNodePresenter()

    @Test
    fun `present should map signal locked breach node to discovered marker state`() {
        // Given
        val node = breachNode(
            phase = BreachNodePhase.SIGNAL_LOCKED,
            canStartUpload = false,
        )

        // When
        val actual = subject.present(node)

        // Then
        actual.kind shouldBe MapObjectKind.BreachNode
        actual.markerState shouldBe BreachMarkerState.DISCOVERED
        actual.title shouldBe "District 7"
        actual.position shouldBe LatLng(latitude = 50.45, longitude = 30.52)
        actual.radiusMeters shouldBe 35
    }

    @Test
    fun `present should map upload ready breach node to upload ready marker state`() {
        // Given
        val node = breachNode(
            phase = BreachNodePhase.DISCOVERED,
            canStartUpload = true,
        )

        // When
        val actual = subject.present(node)

        // Then
        actual.markerState shouldBe BreachMarkerState.UPLOAD_READY
    }

    @Test
    fun `present should map controlled breach node to controlled marker state`() {
        // Given
        val node = breachNode(
            phase = BreachNodePhase.CONTROLLED,
            canStartUpload = false,
        )

        // When
        val actual = subject.present(node)

        // Then
        actual.markerState shouldBe BreachMarkerState.CONTROLLED
    }

    @Test
    fun `present should map lockdown breach node to lockdown marker state`() {
        // Given
        val node = breachNode(
            phase = BreachNodePhase.LOCKDOWN,
            canStartUpload = false,
        )

        // When
        val actual = subject.present(node)

        // Then
        actual.markerState shouldBe BreachMarkerState.LOCKDOWN
    }

    private fun breachNode(
        phase: BreachNodePhase,
        canStartUpload: Boolean,
    ): BreachNode =
        BreachNode(
            definition = BreachNodeDefinition(
                id = "breach-node:v1:h3r9:cell-7",
                h3CellId = "cell-7",
                districtName = "District 7",
                description = "Recovered node",
                location = GeoPoint(lat = 50.45, lon = 30.52),
                interactionRadiusMeters = 35.0,
                controlledH3CellIds = setOf("cell-7"),
            ),
            state = BreachNodeState(
                breachNodeId = "breach-node:v1:h3r9:cell-7",
                h3CellId = "cell-7",
                discoveredAt = Instant.parse("2026-05-15T12:00:00Z"),
                controlledAt = null,
                lockdownUntil = null,
                updatedAt = Instant.parse("2026-05-15T12:00:00Z"),
            ),
            phase = phase,
            distanceMeters = 18.0,
            canDiscover = false,
            canStartUpload = canStartUpload,
        )
}
