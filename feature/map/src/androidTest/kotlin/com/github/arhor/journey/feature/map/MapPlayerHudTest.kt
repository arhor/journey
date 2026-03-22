package com.github.arhor.journey.feature.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arhor.journey.core.common.ResourceType
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapPlayerHudTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `MapPlayerHud should invoke hero and settings callbacks when their buttons are clicked`() {
        // Given
        var heroClicks = 0
        var settingsClicks = 0
        composeRule.setContent {
            MaterialTheme {
                MapPlayerHud(
                    state = contentState(),
                    onHeroClick = { heroClicks += 1 },
                    onSettingsClick = { settingsClicks += 1 },
                )
            }
        }

        // When
        composeRule.onNodeWithTag(MAP_HUD_HERO_BUTTON_TEST_TAG).performClick()
        composeRule.onNodeWithTag(MAP_HUD_SETTINGS_BUTTON_TEST_TAG).performClick()

        // Then
        heroClicks shouldBe 1
        settingsClicks shouldBe 1
    }

    @Test
    fun `MapPlayerHud should render resource amounts when content state is provided`() {
        // Given
        composeRule.setContent {
            MaterialTheme {
                MapPlayerHud(
                    state = contentState(),
                    onHeroClick = {},
                    onSettingsClick = {},
                )
            }
        }

        // When
        val hud = composeRule.onNodeWithTag(MAP_HUD_TEST_TAG)
        val woodAmount = composeRule.onNodeWithText("1.2K")
        val coalAmount = composeRule.onNodeWithText("12K")
        val stoneAmount = composeRule.onNodeWithText("1.3M")

        // Then
        hud.assertIsDisplayed()
        woodAmount.assertIsDisplayed()
        coalAmount.assertIsDisplayed()
        stoneAmount.assertIsDisplayed()
    }

    private fun contentState(): MapHudUiState.Content =
        MapHudUiState.Content(
            heroInitial = "A",
            levelLabel = "Lv 7",
            resources = listOf(
                MapHudResourceUiModel(
                    resourceType = ResourceType.WOOD,
                    amount = 1_250,
                    amountLabel = "1.2K",
                ),
                MapHudResourceUiModel(
                    resourceType = ResourceType.COAL,
                    amount = 12_300,
                    amountLabel = "12K",
                ),
                MapHudResourceUiModel(
                    resourceType = ResourceType.STONE,
                    amount = 1_300_000,
                    amountLabel = "1.3M",
                ),
            ),
        )
}
