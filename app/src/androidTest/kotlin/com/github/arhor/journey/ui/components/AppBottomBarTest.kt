package com.github.arhor.journey.ui.components

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.arhor.journey.MainActivity
import com.github.arhor.journey.ui.navigation.BottomNavItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppBottomBarTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `bottomBar should select settings tab when settings item is clicked from home`() {
        // Given
        composeRule.onNodeWithTag(BottomNavItem.HomeItem.testTag).assertIsSelected()
        composeRule.onNodeWithTag(BottomNavItem.SettingsItem.testTag).assertIsNotSelected()

        // When
        composeRule.onNodeWithTag(BottomNavItem.SettingsItem.testTag).performClick()

        // Then
        composeRule.onNodeWithTag(BottomNavItem.HomeItem.testTag).assertIsNotSelected()
        composeRule.onNodeWithTag(BottomNavItem.SettingsItem.testTag).assertIsSelected()
    }
}
