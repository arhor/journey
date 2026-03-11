package com.github.arhor.journey.domain.model

data class ProgressionApplicationResult(
    val hero: Hero,
    val levelUps: Int,
    val levelUpBonus: StatsDelta,
)
