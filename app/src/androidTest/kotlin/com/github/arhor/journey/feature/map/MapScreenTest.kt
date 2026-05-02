package com.github.arhor.journey.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arhor.journey.R
import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.feature.map.fow.model.FogOfWarUiState
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `MapContent should show startup splash overlay when startup gate is visible`() {
        // Given
        composeRule.setContent {
            MaterialTheme {
                MapContent(
                    state = contentState(isStartupSplashVisible = true),
                    hudState = hudState(),
                    dispatch = {},
                    onOpenHero = {},
                    onOpenSettings = {},
                    mapContent = { modifier, _ -> Box(modifier = modifier) },
                )
            }
        }

        // Then
        composeRule.onNodeWithTag(MAP_STARTUP_SPLASH_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Preparing map view and acquiring your location...").assertIsDisplayed()
    }

    @Test
    fun `MapContent should hide startup splash overlay when startup gate is not visible`() {
        // Given
        composeRule.setContent {
            MaterialTheme {
                MapContent(
                    state = contentState(isStartupSplashVisible = false),
                    hudState = hudState(),
                    dispatch = {},
                    onOpenHero = {},
                    onOpenSettings = {},
                    mapContent = { modifier, _ -> Box(modifier = modifier) },
                )
            }
        }

        // Then
        composeRule.onAllNodesWithTag(MAP_STARTUP_SPLASH_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun `MapContent should block HUD interaction while startup splash overlay is visible`() {
        // Given
        var heroClicks = 0
        composeRule.setContent {
            MaterialTheme {
                MapContent(
                    state = contentState(isStartupSplashVisible = true),
                    hudState = hudState(),
                    dispatch = {},
                    onOpenHero = { heroClicks += 1 },
                    onOpenSettings = {},
                    mapContent = { modifier, _ -> Box(modifier = modifier) },
                )
            }
        }

        // When
        composeRule.onNodeWithTag(MAP_HUD_HERO_BUTTON_TEST_TAG).performClick()

        // Then
        heroClicks shouldBe 0
    }

    private fun contentState(isStartupSplashVisible: Boolean): MapUiState.Content =
        MapUiState.Content(
            northResetRequestToken = 0,
            isExplorationTrackingActive = true,
            explorationTrackingCadence = ExplorationTrackingCadence.FOREGROUND,
            explorationTrackingStatus = ExplorationTrackingStatus.TRACKING,
            isStartupSplashVisible = isStartupSplashVisible,
            startupSplashMessage = R.string.map_view_startup_loading_message,
            mapStyleUri = "asset://map/styles/cyberpunk.json",
            visibleObjects = emptyList(),
            selectedWatchtower = null,
            fogOfWar = FogOfWarUiState(),
        )

    private fun hudState(): MapHudUiState.Content =
        MapHudUiState.Content(
            heroInitial = "A",
            levelLabel = "Lv 7",
            xpInLevel = 4_850,
            xpToNextLevel = 7_500,
            resources = listOf(
                MapHudResourceUiModel(
                    resourceType = ResourceType.SCRAP,
                    amount = 1_250,
                    amountLabel = "1.2K",
                ),
                MapHudResourceUiModel(
                    resourceType = ResourceType.COMPONENTS,
                    amount = 12_300,
                    amountLabel = "12K",
                ),
                MapHudResourceUiModel(
                    resourceType = ResourceType.FUEL,
                    amount = 1_300_000,
                    amountLabel = "1.3M",
                ),
            ),
        )
}
