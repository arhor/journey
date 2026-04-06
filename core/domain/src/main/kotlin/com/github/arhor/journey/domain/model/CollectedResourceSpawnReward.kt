package com.github.arhor.journey.domain.model

data class CollectedResourceSpawnReward(
    val spawnId: String,
    val resourceTypeId: String,
    val amountAwarded: Int,
)
