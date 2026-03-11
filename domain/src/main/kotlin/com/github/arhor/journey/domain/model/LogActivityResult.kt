package com.github.arhor.journey.domain.model

data class LogActivityResult(
    val logEntryId: Long,
    val reward: Reward,
    val heroBefore: Hero,
    val heroAfter: Hero,
    val levelUps: Int,
)
