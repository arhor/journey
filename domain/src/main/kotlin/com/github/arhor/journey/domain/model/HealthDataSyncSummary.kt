package com.github.arhor.journey.domain.model

import java.time.Duration

data class HealthDataSyncSummary(
    val importedEntriesCount: Int,
    val importedStepsCount: Long,
    val importedSessionsCount: Int,
    val importedSessionDuration: Duration,
)
