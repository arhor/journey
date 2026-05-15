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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arhor.journey.R
import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.feature.map.fow.model.FogOfWarUiState
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
                    dispatch = {},
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
                    dispatch = {},
                    mapContent = { modifier, _ -> Box(modifier = modifier) },
                )
            }
        }

        // Then
        composeRule.onAllNodesWithTag(MAP_STARTUP_SPLASH_TEST_TAG).assertCountEquals(0)
    }

    private fun contentState(isStartupSplashVisible: Boolean): MapUiState.Content =
        MapUiState.Content(
            northResetRequestToken = 0,
            isExplorationTrackingActive = true,
            explorationTrackingCadence = ExplorationTrackingCadence.FOREGROUND,
            explorationTrackingStatus = ExplorationTrackingStatus.TRACKING,
            breachProtocol = BreachProtocolUiState.Idle,
            isStartupSplashVisible = isStartupSplashVisible,
            startupSplashMessage = R.string.map_view_startup_loading_message,
            mapStyleUri = "asset://map/styles/cyberpunk.json",
            visibleObjects = emptyList(),
            fogOfWar = FogOfWarUiState(),
        )
}
