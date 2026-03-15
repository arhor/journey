package com.github.arhor.journey.feature.map

import com.github.arhor.journey.feature.map.R
import io.kotest.matchers.shouldBe
import org.junit.Test

class MapNavigationContractTest {

    @Test
    fun `mapBottomNavDestination should expose map destination metadata when configuring bottom bar`() {
        // Given
        val expectedDestination = MapDestination
        val expectedLabelRes = R.string.map_nav_label
        val expectedTestTag = "bottomNav:map"

        // When
        val actual = mapBottomNavDestination

        // Then
        actual.destination shouldBe expectedDestination
        actual.labelRes shouldBe expectedLabelRes
        actual.testTag shouldBe expectedTestTag
    }
}
