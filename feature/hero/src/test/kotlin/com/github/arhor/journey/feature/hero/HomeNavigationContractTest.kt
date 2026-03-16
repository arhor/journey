package com.github.arhor.journey.feature.hero

import io.kotest.matchers.shouldBe
import org.junit.Test

class HomeNavigationContractTest {

    @Test
    fun `homeBottomNavDestination should expose home destination metadata when configuring bottom bar`() {
        // Given
        val expectedDestination = HomeDestination
        val expectedLabelRes = R.string.home_nav_label
        val expectedTestTag = "bottomNav:home"

        // When
        val actual = homeBottomNavDestination

        // Then
        actual.destination shouldBe expectedDestination
        actual.labelRes shouldBe expectedLabelRes
        actual.testTag shouldBe expectedTestTag
    }
}
