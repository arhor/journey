package com.github.arhor.journey.domain.activity.model

data class ActivityLogInsertResult(
    val logEntryId: Long,
    val shouldApplyReward: Boolean,
)
