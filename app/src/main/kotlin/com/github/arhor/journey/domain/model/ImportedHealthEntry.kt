package com.github.arhor.journey.domain.model

import java.time.Instant

data class ImportedHealthEntry(
    val sourceId: String,
    val type: HealthDataType,
    val startTime: Instant,
    val endTime: Instant,
    val value: Long,
)
