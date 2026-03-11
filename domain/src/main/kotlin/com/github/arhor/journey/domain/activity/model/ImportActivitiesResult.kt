package com.github.arhor.journey.domain.activity.model

import com.github.arhor.journey.domain.player.model.Hero
import com.github.arhor.journey.domain.model.Reward

data class ImportActivitiesResult(
    val heroBefore: Hero,
    val heroAfter: Hero,
    val importedCount: Int,
    val rewardedCount: Int,
    val skippedRewardCount: Int,
    val totalReward: Reward,
    val totalLevelUps: Int,
)
