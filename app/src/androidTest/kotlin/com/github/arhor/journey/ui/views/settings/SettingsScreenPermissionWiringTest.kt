package com.github.arhor.journey.ui.views.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.github.arhor.journey.R
import com.github.arhor.journey.domain.model.DistanceUnit
import com.github.arhor.journey.domain.model.HealthConnectAvailability
import com.github.arhor.journey.ui.views.settings.model.HealthConnectConnectionStatus
import com.github.arhor.journey.ui.views.settings.model.HealthConnectPermissionStatus
import com.github.arhor.journey.ui.views.settings.model.ImportedActivitySummary
import io.kotest.matchers.collections.shouldContainInOrder
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class SettingsScreenPermissionWiringTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `settings health connect actions should dispatch permission-related intents when action buttons are clicked`() {
        // Given
        val dispatchedIntents = mutableListOf<SettingsIntent>()
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState.Content(
                    isUpdating = false,
                    distanceUnit = DistanceUnit.METRIC,
                    healthConnectAvailability = HealthConnectAvailability.AVAILABLE,
                    healthConnectConnectionStatus = HealthConnectConnectionStatus.DISCONNECTED,
                    healthConnectPermissionStatus = HealthConnectPermissionStatus.NOT_REQUESTED,
                    missingHealthConnectPermissions = emptySet(),
                    lastSyncTimestamp = Instant.parse("2026-01-01T00:00:00Z"),
                    isSyncInProgress = false,
                    importedTodaySummary = ImportedActivitySummary(),
                    importedWeekSummary = ImportedActivitySummary(),
                ),
                dispatch = { dispatchedIntents += it },
            )
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connectLabel = context.getString(R.string.settings_health_connect_connect_action)
        val mapCreditsTitle = context.getString(R.string.settings_map_credits_title)
        val mapLibreCredits = context.getString(R.string.settings_map_credits_maplibre)
        val openFreeMapCredits = context.getString(R.string.settings_map_credits_openfreemap)
        val managePermissionsLabel = context.getString(R.string.settings_health_connect_manage_permissions_action)

        // When
        composeRule.onNodeWithText(connectLabel).performClick()
        composeRule.onNodeWithText(managePermissionsLabel).performClick()

        // Then
        composeRule.onNodeWithText(mapCreditsTitle).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(mapLibreCredits).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(openFreeMapCredits).performScrollTo().assertIsDisplayed()
        dispatchedIntents.shouldContainInOrder(
            SettingsIntent.ConnectHealthConnect,
            SettingsIntent.ManageHealthConnectPermissions,
        )
    }
}
