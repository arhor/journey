package com.github.arhor.journey.domain.model

import com.github.arhor.journey.domain.player.model.Hero
import com.github.arhor.journey.domain.player.model.StatsDelta

data class ProgressionApplicationResult(
    val hero: Hero,
    val levelUps: Int,
    val levelUpBonus: StatsDelta,
)
