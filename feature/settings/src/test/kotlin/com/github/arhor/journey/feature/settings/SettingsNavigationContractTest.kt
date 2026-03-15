package com.github.arhor.journey.feature.settings

import com.github.arhor.journey.feature.settings.R
import io.kotest.matchers.shouldBe
import org.junit.Test

class SettingsNavigationContractTest {

    @Test
    fun `settingsBottomNavDestination should expose settings destination metadata when configuring bottom bar`() {
        // Given
        val expectedDestination = SettingsDestination
        val expectedLabelRes = R.string.settings_nav_label
        val expectedTestTag = "bottomNav:settings"

        // When
        val actual = settingsBottomNavDestination

        // Then
        actual.destination shouldBe expectedDestination
        actual.labelRes shouldBe expectedLabelRes
        actual.testTag shouldBe expectedTestTag
    }
}
