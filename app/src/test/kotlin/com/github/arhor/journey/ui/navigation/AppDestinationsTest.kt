package com.github.arhor.journey.ui.navigation

import com.github.arhor.journey.feature.home.homeBottomNavDestination
import com.github.arhor.journey.feature.map.mapBottomNavDestination
import com.github.arhor.journey.feature.settings.settingsBottomNavDestination
import io.kotest.matchers.shouldBe
import org.junit.Test

class AppDestinationsTest {

    @Test
    fun `bottomNavDestinations should keep expected feature order when rendering app bottom bar`() {
        // Given
        val expected = listOf(
            homeBottomNavDestination,
            mapBottomNavDestination,
            settingsBottomNavDestination,
        )

        // When
        val actual = bottomNavDestinations

        // Then
        actual shouldBe expected
    }

    @Test
    fun `bottomNavDestinations should use unique test tags when exposing navigation items`() {
        // Given
        val destinations = bottomNavDestinations

        // When
        val uniqueTestTagsCount = destinations.map { it.testTag }.distinct().size

        // Then
        uniqueTestTagsCount shouldBe destinations.size
        destinations.isNotEmpty() shouldBe true
    }
}
