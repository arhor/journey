package com.github.arhor.journey.feature.map.presentation

import com.github.arhor.journey.domain.model.BreachNode
import com.github.arhor.journey.domain.model.BreachNodeDefinition
import com.github.arhor.journey.domain.model.BreachNodePhase
import com.github.arhor.journey.domain.model.BreachNodeState
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.feature.map.BreachDirectionalGuidanceUiState
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant

class BreachDirectionalGuidancePresenterTest {

    private val subject = BreachDirectionalGuidancePresenter()

    @Test
    fun `present should return floating arrow when actor is outside upload radius`() {
        // Given
        val actorLocation = GeoPoint(lat = 0.0, lon = 0.0)
        val breach = breachNode(
            location = GeoPoint(lat = 0.0, lon = 1.0),
            interactionRadiusMeters = 30.0,
            canStartUpload = false,
        )

        // When
        val actual = subject.present(
            breach = breach,
            actorLocation = actorLocation,
        )

        // Then
        actual shouldBe BreachDirectionalGuidanceUiState.FloatingArrow(
            breachNodeId = breach.definition.id,
            districtName = breach.definition.districtName,
            bearingDegrees = 90.0,
            distanceMeters = actorLocation.distanceTo(breach.definition.location).toInt(),
            canStartUpload = false,
        )
    }

    @Test
    fun `present should return on target when actor can start upload`() {
        // Given
        val actorLocation = GeoPoint(lat = 50.45, lon = 30.52)
        val breach = breachNode(
            location = actorLocation,
            interactionRadiusMeters = 30.0,
            canStartUpload = true,
        )

        // When
        val actual = subject.present(
            breach = breach,
            actorLocation = actorLocation,
        )

        // Then
        actual shouldBe BreachDirectionalGuidanceUiState.OnTarget(
            breachNodeId = breach.definition.id,
            districtName = breach.definition.districtName,
            distanceMeters = 0,
            canStartUpload = true,
        )
    }

    @Test
    fun `present should return unavailable when actor location is missing`() {
        // Given
        val breach = breachNode(
            location = GeoPoint(lat = 50.45, lon = 30.52),
            interactionRadiusMeters = 30.0,
            canStartUpload = false,
        )

        // When
        val actual = subject.present(
            breach = breach,
            actorLocation = null,
        )

        // Then
        actual shouldBe BreachDirectionalGuidanceUiState.Unavailable(
            breachNodeId = breach.definition.id,
            districtName = breach.definition.districtName,
            message = "Location required to continue breach scan.",
        )
    }

    private fun breachNode(
        location: GeoPoint,
        interactionRadiusMeters: Double,
        canStartUpload: Boolean,
    ): BreachNode =
        BreachNode(
            definition = BreachNodeDefinition(
                id = "breach-node:v1:h3r9:test-cell",
                h3CellId = "test-cell",
                districtName = "Downtown",
                description = "Signal source",
                location = location,
                interactionRadiusMeters = interactionRadiusMeters,
                controlledH3CellIds = setOf("test-cell"),
            ),
            state = BreachNodeState(
                breachNodeId = "breach-node:v1:h3r9:test-cell",
                h3CellId = "test-cell",
                discoveredAt = Instant.parse("2026-05-01T10:15:30Z"),
                controlledAt = null,
                lockdownUntil = null,
                updatedAt = Instant.parse("2026-05-01T10:15:30Z"),
            ),
            phase = BreachNodePhase.SIGNAL_LOCKED,
            distanceMeters = if (canStartUpload) 0.0 else 111_194.9,
            canDiscover = canStartUpload,
            canStartUpload = canStartUpload,
        )
}
