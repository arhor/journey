package com.github.arhor.journey.feature.map

import com.github.arhor.journey.domain.model.GeoBounds
import io.kotest.matchers.shouldBe
import org.junit.Test

class MapObjectQueryWindowPolicyTest {

    private val subject = MapObjectQueryWindowPolicy()

    @Test
    fun `resolveQueryWindow should reuse current window when visible bounds stay inside it`() {
        // Given
        val initialVisibleBounds = GeoBounds(
            south = 10.0,
            west = 20.0,
            north = 11.0,
            east = 21.0,
        )
        val shiftedVisibleBounds = GeoBounds(
            south = 10.1,
            west = 20.1,
            north = 11.1,
            east = 21.1,
        )
        val initialQueryWindow = subject.resolveQueryWindow(
            visibleBounds = initialVisibleBounds,
            currentQueryWindow = null,
        )

        // When
        val actual = subject.resolveQueryWindow(
            visibleBounds = shiftedVisibleBounds,
            currentQueryWindow = initialQueryWindow,
        )

        // Then
        actual shouldBe initialQueryWindow
    }

    @Test
    fun `resolveQueryWindow should create a new buffered window when visible bounds leave current window`() {
        // Given
        val initialVisibleBounds = GeoBounds(
            south = 10.0,
            west = 20.0,
            north = 11.0,
            east = 21.0,
        )
        val outrunVisibleBounds = GeoBounds(
            south = 10.0,
            west = 22.0,
            north = 11.0,
            east = 23.0,
        )
        val initialQueryWindow = subject.resolveQueryWindow(
            visibleBounds = initialVisibleBounds,
            currentQueryWindow = null,
        )

        // When
        val actual = subject.resolveQueryWindow(
            visibleBounds = outrunVisibleBounds,
            currentQueryWindow = initialQueryWindow,
        )

        // Then
        actual shouldBe GeoBounds(
            south = 9.5,
            west = 21.5,
            north = 11.5,
            east = 23.5,
        )
    }

    @Test
    fun `resolveQueryWindow should clamp buffered window to supported map bounds`() {
        // Given
        val visibleBounds = GeoBounds(
            south = -85.0,
            west = -179.9,
            north = 85.0,
            east = 179.9,
        )

        // When
        val actual = subject.resolveQueryWindow(
            visibleBounds = visibleBounds,
            currentQueryWindow = null,
        )

        // Then
        actual shouldBe GeoBounds(
            south = -85.05112878,
            west = -180.0,
            north = 85.05112878,
            east = 180.0 - 1e-9,
        )
    }
}
