package com.github.arhor.journey.data.local.seed

import com.github.arhor.journey.domain.player.model.Hero
import com.github.arhor.journey.domain.player.model.HeroEnergy
import com.github.arhor.journey.domain.player.model.HeroStats
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
            energy = HeroEnergy(current = 100, max = 100),
            createdAt = now,
            updatedAt = now,
        )
}

