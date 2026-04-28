package com.github.arhor.journey.feature.settings

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SettingsViewModelTest {

    @Test
    fun `uiState should expose static content`() = runTest {
        // Given
        val viewModel = SettingsViewModel()

        // When
        val actual = viewModel.uiState.first()

        // Then
        actual shouldBe SettingsUiState.Content
    }
}
