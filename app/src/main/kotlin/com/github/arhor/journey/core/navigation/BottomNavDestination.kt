package com.github.arhor.journey.core.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavDestination<T : Any>(
    val destination: T,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val testTag: String,
)
