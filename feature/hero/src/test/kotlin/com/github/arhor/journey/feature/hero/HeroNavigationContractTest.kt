package com.github.arhor.journey.feature.hero

import io.kotest.matchers.shouldBe
import org.junit.Test

class HeroNavigationContractTest {

    @Test
    fun `HeroBottomNavDestination should expose hero destination metadata when configuring bottom bar`() {
        // Given
        val expectedDestination = HeroDestination
        val expectedLabelRes = R.string.hero_nav_label
        val expectedTestTag = "bottomNav:hero"

        // When
        val actual = HeroBottomNavDestination

        // Then
        actual.destination shouldBe expectedDestination
        actual.labelRes shouldBe expectedLabelRes
        actual.testTag shouldBe expectedTestTag
    }
}
