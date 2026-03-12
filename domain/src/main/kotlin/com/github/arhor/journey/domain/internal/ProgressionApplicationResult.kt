package com.github.arhor.journey.domain.internal

import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.StatsDelta

data class ProgressionApplicationResult(
    val hero: Hero,
    val levelUps: Int,
    val levelUpBonus: StatsDelta,
)
