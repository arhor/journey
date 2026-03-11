package com.github.arhor.journey.domain.progression

import com.github.arhor.journey.domain.player.model.Hero
import com.github.arhor.journey.domain.model.Progression
import com.github.arhor.journey.domain.model.ProgressionApplicationResult
import com.github.arhor.journey.domain.model.Reward
import com.github.arhor.journey.domain.player.model.StatsDelta
import java.time.Instant
import javax.inject.Inject

/**
 * Applies rewards to hero state deterministically.
 *
 * This stays pure and easy to unit-test: no I/O and no dependency on Android components.
 */
class ProgressionEngine @Inject constructor(
    private val policy: ProgressionPolicy,
) {

    private val levelUpBonusPerLevel = StatsDelta(strength = 1, vitality = 1, dexterity = 1, stamina = 1)

    fun applyReward(
        hero: Hero,
        reward: Reward,
        now: Instant,
    ): ProgressionApplicationResult {
        val startingLevel = hero.progression.level.coerceAtLeast(1)
        var level = startingLevel
        var xpInLevel = hero.progression.xpInLevel.coerceAtLeast(0L) + reward.xp.coerceAtLeast(0L)

        var stats = hero.stats + reward.stats

        var levelUps = 0
        var levelUpBonus = StatsDelta()

        while (xpInLevel >= policy.xpToNextLevel(level)) {
            xpInLevel -= policy.xpToNextLevel(level)
            level += 1
            levelUps += 1

            stats += levelUpBonusPerLevel
            levelUpBonus = StatsDelta(
                strength = levelUpBonus.strength + levelUpBonusPerLevel.strength,
                vitality = levelUpBonus.vitality + levelUpBonusPerLevel.vitality,
                dexterity = levelUpBonus.dexterity + levelUpBonusPerLevel.dexterity,
                stamina = levelUpBonus.stamina + levelUpBonusPerLevel.stamina,
            )
        }

        val updatedHero = hero.copy(
            stats = stats,
            progression = Progression(level = level, xpInLevel = xpInLevel),
            updatedAt = now,
        )

        return ProgressionApplicationResult(
            hero = updatedHero,
            levelUps = levelUps,
            levelUpBonus = levelUpBonus,
        )
    }
}

