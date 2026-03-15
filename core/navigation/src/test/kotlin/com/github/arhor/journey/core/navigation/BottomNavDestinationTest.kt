package com.github.arhor.journey.core.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test

class BottomNavDestinationTest {

    @Test
    fun `constructor should keep provided contract values when destination is created`() {
        // Given
        val icon = testIcon(name = "home")
        val destination = BottomNavDestination(
            destination = "home-route",
            labelRes = 101,
            icon = icon,
            testTag = "bottomNav:home",
        )

        // When
        val actual = destination

        // Then
        actual.destination shouldBe "home-route"
        actual.labelRes shouldBe 101
        actual.icon shouldBe icon
        actual.testTag shouldBe "bottomNav:home"
    }

    @Test
    fun `equals should return false when only test tag differs`() {
        // Given
        val icon = testIcon(name = "map")
        val first = BottomNavDestination(
            destination = "map-route",
            labelRes = 201,
            icon = icon,
            testTag = "bottomNav:map",
        )
        val second = first.copy(testTag = "bottomNav:map-alt")

        // When
        val areEqual = first == second

        // Then
        areEqual shouldBe false
        first shouldNotBe second
    }

    @Test
    fun `equals should return true and hashCode should match when all properties are the same`() {
        // Given
        val icon = testIcon(name = "settings")
        val first = BottomNavDestination(
            destination = SettingsRoute,
            labelRes = 301,
            icon = icon,
            testTag = "bottomNav:settings",
        )
        val second = BottomNavDestination(
            destination = SettingsRoute,
            labelRes = 301,
            icon = icon,
            testTag = "bottomNav:settings",
        )

        // When
        val areEqual = first == second
        val hasSameHashCode = first.hashCode() == second.hashCode()

        // Then
        areEqual shouldBe true
        hasSameHashCode shouldBe true
    }

    @Test
    fun `copy should update only overridden property when destination changes`() {
        // Given
        val icon = testIcon(name = "home")
        val initial = BottomNavDestination(
            destination = Route("home"),
            labelRes = 401,
            icon = icon,
            testTag = "bottomNav:home",
        )

        // When
        val actual = initial.copy(destination = Route("map"))

        // Then
        actual.destination shouldBe Route("map")
        actual.labelRes shouldBe 401
        actual.icon shouldBe icon
        actual.testTag shouldBe "bottomNav:home"
    }

    @Test
    fun `equals should return false when destination value differs with all other fields equal`() {
        // Given
        val icon = testIcon(name = "poi")
        val first = BottomNavDestination(
            destination = Route("home"),
            labelRes = 501,
            icon = icon,
            testTag = "bottomNav:route",
        )
        val second = first.copy(destination = Route("map"))

        // When
        val areEqual = first == second

        // Then
        areEqual shouldBe false
        first shouldNotBe second
    }

    @Test
    fun `equals should return false when compared to a different type`() {
        // Given
        val destination = BottomNavDestination(
            destination = "home-route",
            labelRes = 601,
            icon = testIcon(name = "home"),
            testTag = "bottomNav:home",
        )

        // When
        val areEqual = destination.equals("home-route")

        // Then
        areEqual shouldBe false
    }

    private data class Route(val value: String)

    private data object SettingsRoute

    private fun testIcon(name: String): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(0f, 0f)
            lineTo(24f, 0f)
            lineTo(24f, 24f)
            lineTo(0f, 24f)
            close()
        }
    }.build()
}
