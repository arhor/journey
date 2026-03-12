package com.github.arhor.journey.domain.player.model

data class ProgressionApplicationResult(
    val hero: Hero,
    val levelUps: Int,
    val levelUpBonus: StatsDelta,
)
