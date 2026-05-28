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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arhor.journey.R
import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.feature.map.fow.model.FogOfWarUiState
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `MapContent should show Pulse button when breach protocol is idle`() {
        // Given
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pulseLabel = context.getString(R.string.breach_pulse_button)

        composeRule.setContent {
            MaterialTheme {
                MapContent(
                    state = contentState(
                        isStartupSplashVisible = false,
                        breachProtocol = BreachProtocolUiState.Idle,
                    ),
                    dispatch = {},
                    mapContent = { modifier, _ -> Box(modifier = modifier) },
                )
            }
        }

        // Then
        composeRule.onNodeWithText(pulseLabel).assertIsDisplayed()
    }

    @Test
    fun `MapContent should show signal strength text when breach protocol is signal locked`() {
        // Given
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val signalStrength = context.getString(R.string.breach_signal_strength, 82)

        composeRule.setContent {
            MaterialTheme {
                MapContent(
                    state = contentState(
                        isStartupSplashVisible = false,
                        breachProtocol = BreachProtocolUiState.SignalLocked(
                            breachNodeId = "breach-node:v1:h3r9:test",
                            districtName = "Downtown",
                            distanceMeters = 12,
                            signalStrengthPercent = 82,
                            canStartUpload = true,
                            disabledReason = null,
                        ),
                    ),
                    dispatch = {},
                    mapContent = { modifier, _ -> Box(modifier = modifier) },
                )
            }
        }

        // Then
        composeRule.onNodeWithText(signalStrength).assertIsDisplayed()
    }

    @Test
    fun `MapContent should show upload progress text when breach protocol is uploading`() {
        // Given
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val progressText = context.getString(R.string.breach_upload_progress, 50)

        composeRule.setContent {
            MaterialTheme {
                MapContent(
                    state = contentState(
                        isStartupSplashVisible = false,
                        breachProtocol = BreachProtocolUiState.Uploading(
                            breachNodeId = "breach-node:v1:h3r9:test",
                            districtName = "Downtown",
                            progressPercent = 50,
                        ),
                    ),
                    dispatch = {},
                    mapContent = { modifier, _ -> Box(modifier = modifier) },
                )
            }
        }

        // Then
        composeRule.onNodeWithText(progressText).assertIsDisplayed()
    }

    @Test
    fun `MapContent should dispatch PulseClicked when Pulse button is tapped`() {
        // Given
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pulseLabel = context.getString(R.string.breach_pulse_button)
        val dispatchedIntents = mutableListOf<MapIntent>()

        composeRule.setContent {
            MaterialTheme {
                MapContent(
                    state = contentState(
                        isStartupSplashVisible = false,
                        breachProtocol = BreachProtocolUiState.Idle,
                    ),
                    dispatch = dispatchedIntents::add,
                    mapContent = { modifier, _ -> Box(modifier = modifier) },
                )
            }
        }

        // When
        composeRule.onNodeWithText(pulseLabel).performClick()

        // Then
        dispatchedIntents shouldContainExactly listOf(MapIntent.PulseClicked)
    }

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

    private fun contentState(
        isStartupSplashVisible: Boolean,
        breachProtocol: BreachProtocolUiState = BreachProtocolUiState.Idle,
    ): MapUiState.Content =
        MapUiState.Content(
            northResetRequestToken = 0,
            isExplorationTrackingActive = true,
            explorationTrackingCadence = ExplorationTrackingCadence.FOREGROUND,
            explorationTrackingStatus = ExplorationTrackingStatus.TRACKING,
            breachProtocol = breachProtocol,
            isStartupSplashVisible = isStartupSplashVisible,
            startupSplashMessage = R.string.map_view_startup_loading_message,
            mapMode = MapMode.Exploration(
                styleUri = "asset://map/styles/light.json",
            ),
            mapStyleUri = "asset://map/styles/cyberpunk.json",
            visibleObjects = emptyList(),
            fogOfWar = FogOfWarUiState(),
        )
}
