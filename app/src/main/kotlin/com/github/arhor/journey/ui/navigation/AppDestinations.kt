package com.github.arhor.journey.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
data object Home

@Serializable
data object Settings

data class BottomNavItem(
    val destination: Any,
    val label: String,
    val icon: ImageVector,
)

val BottomNavItems = listOf(
    BottomNavItem(
        destination = Home,
        label = "Home",
        icon = Icons.Outlined.Groups,
    ),
    BottomNavItem(
        destination = Settings,
        label = "Settings",
        icon = Icons.Outlined.Settings,
    ),
)
