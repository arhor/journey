package com.github.arhor.journey.feature.map.viewinterop

import io.kotest.matchers.shouldBe
import org.junit.Test

class MapLibreViewMapScreenAltitudeNormalizationTest {

    @Test
    fun `normalizeCenterAltitudeMeters should return null for non-finite altitude values`() {
        // Given
        val nanAltitude = Double.NaN
        val infiniteAltitude = Double.POSITIVE_INFINITY

        // When
        val normalizedNan = normalizeCenterAltitudeMeters(nanAltitude)
        val normalizedInfinite = normalizeCenterAltitudeMeters(infiniteAltitude)

        // Then
        normalizedNan shouldBe null
        normalizedInfinite shouldBe null
    }

    @Test
    fun `normalizeCenterAltitudeMeters should preserve finite altitude values`() {
        // Given
        val belowSeaLevel = -10.0
        val seaLevel = 0.0
        val inRange = 1234.0
        val highAltitude = 90_000.0

        // When
        val normalizedBelowSeaLevel = normalizeCenterAltitudeMeters(belowSeaLevel)
        val normalizedSeaLevel = normalizeCenterAltitudeMeters(seaLevel)
        val normalizedInRange = normalizeCenterAltitudeMeters(inRange)
        val normalizedHighAltitude = normalizeCenterAltitudeMeters(highAltitude)

        // Then
        normalizedBelowSeaLevel shouldBe belowSeaLevel
        normalizedSeaLevel shouldBe seaLevel
        normalizedInRange shouldBe inRange
        normalizedHighAltitude shouldBe highAltitude
    }
}
