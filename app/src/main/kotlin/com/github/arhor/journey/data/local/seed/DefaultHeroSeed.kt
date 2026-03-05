package com.github.arhor.journey.data.local.seed

import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroStats
import com.github.arhor.journey.domain.model.Progression
import java.time.Instant

/**
 * Default hero created on first launch / first access.
 */
object DefaultHeroSeed {
    const val CURRENT_HERO_ID: String = "player"

    fun create(now: Instant): Hero =
        Hero(
            id = CURRENT_HERO_ID,
            name = "Adventurer",
            stats = HeroStats(
                strength = 1,
                vitality = 1,
                dexterity = 1,
                stamina = 1,
            ),
            progression = Progression(
                level = 1,
                xpInLevel = 0L,
            ),
            createdAt = now,
            updatedAt = now,
        )
}

