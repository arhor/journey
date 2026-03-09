package com.github.arhor.journey.ui.views.settings.model

import androidx.compose.runtime.Immutable

@Immutable
data class ImportedActivitySummary(
    val importedActivities: Int = 0,
    val importedSteps: Long = 0,
)
