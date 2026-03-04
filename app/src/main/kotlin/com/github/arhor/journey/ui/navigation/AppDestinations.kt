package com.github.arhor.journey.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.github.arhor.journey.R
import kotlinx.serialization.Serializable

@Serializable
data object Home

@Serializable
data object Settings

sealed class BottomNavItem<T : Any>(
    val destination: T,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val testTag: String,
) {

    data object HomeItem : BottomNavItem<Home>(
        destination = Home,
        labelRes = R.string.nav_home,
        icon = Icons.Outlined.Groups,
        testTag = "bottomNav:home",
    )

    data object SettingsItem : BottomNavItem<Settings>(
        destination = Settings,
        labelRes = R.string.nav_settings,
        icon = Icons.Outlined.Settings,
        testTag = "bottomNav:settings",
    )
}

val BottomNavItems = listOf(
    BottomNavItem.HomeItem,
    BottomNavItem.SettingsItem,
)
