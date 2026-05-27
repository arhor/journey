package com.github.arhor.journey.domain.model

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.Test

class GeoPointTest {

    @Test
    fun `bearingTo should return north bearing when target is due north`() {
        // Given
        val origin = GeoPoint(lat = 0.0, lon = 0.0)
        val target = GeoPoint(lat = 1.0, lon = 0.0)

        // When
        val actual = origin.bearingTo(target)

        // Then
        actual shouldBe (0.0 plusOrMinus 0.001)
    }

    @Test
    fun `bearingTo should return east bearing when target is due east`() {
        // Given
        val origin = GeoPoint(lat = 0.0, lon = 0.0)
        val target = GeoPoint(lat = 0.0, lon = 1.0)

        // When
        val actual = origin.bearingTo(target)

        // Then
        actual shouldBe (90.0 plusOrMinus 0.001)
    }
}
