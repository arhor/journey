package com.github.arhor.journey.domain.model

import java.time.Instant

data class HealthDataTimeRange(
    val startTime: Instant,
    val endTime: Instant,
)
