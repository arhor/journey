package com.github.arhor.journey.domain.activity.model

import com.github.arhor.journey.domain.player.model.Hero
import com.github.arhor.journey.domain.model.Reward

data class LogActivityResult(
    val logEntryId: Long,
    val reward: Reward,
    val heroBefore: Hero,
    val heroAfter: Hero,
    val levelUps: Int,
)
