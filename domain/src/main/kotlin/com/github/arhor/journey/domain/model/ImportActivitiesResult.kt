package com.github.arhor.journey.domain.model

data class ImportActivitiesResult(
    val heroBefore: Hero,
    val heroAfter: Hero,
    val importedCount: Int,
    val rewardedCount: Int,
    val skippedRewardCount: Int,
    val totalReward: Reward,
    val totalLevelUps: Int,
)
