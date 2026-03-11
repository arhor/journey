package com.github.arhor.journey.domain.model

data class HealthDataSyncPayload(
    val summary: HealthDataSyncSummary,
    val importedEntries: List<ImportedHealthEntry>,
)
