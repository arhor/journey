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
