package com.github.arhor.journey.domain.model

data class ActivityLogInsertResult(
    val logEntryId: Long,
    val shouldApplyReward: Boolean,
)
