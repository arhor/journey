package com.github.arhor.journey.domain.progression

import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroStats
import com.github.arhor.journey.domain.model.Progression
import com.github.arhor.journey.domain.model.Reward
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class ProgressionEngineTest {

    private val engine = ProgressionEngine(policy = ProgressionPolicy())

    @Test
    fun `applyReward levels up and grants level up stats`() {
        val hero = baseHero(
            progression = Progression(level = 1, xpInLevel = 990L),
            stats = HeroStats(strength = 1, vitality = 1, dexterity = 1, stamina = 1),
        )

        val result = engine.applyReward(
            hero = hero,
            reward = Reward(xp = 20L),
            now = Instant.parse("2026-01-01T00:10:00Z"),
        )

        assertThat(result.levelUps).isEqualTo(1)
        assertThat(result.hero.progression.level).isEqualTo(2)
        assertThat(result.hero.progression.xpInLevel).isEqualTo(10L)
        assertThat(result.hero.stats.strength).isEqualTo(2)
        assertThat(result.hero.stats.vitality).isEqualTo(2)
        assertThat(result.hero.stats.dexterity).isEqualTo(2)
        assertThat(result.hero.stats.stamina).isEqualTo(2)
    }

    @Test
    fun `applyReward can level up multiple times`() {
        val hero = baseHero(
            progression = Progression(level = 1, xpInLevel = 0L),
            stats = HeroStats(strength = 1, vitality = 1, dexterity = 1, stamina = 1),
        )

        val result = engine.applyReward(
            hero = hero,
            reward = Reward(xp = 3000L),
            now = Instant.parse("2026-01-01T00:10:00Z"),
        )

        assertThat(result.levelUps).isEqualTo(2)
        assertThat(result.hero.progression.level).isEqualTo(3)
        assertThat(result.hero.progression.xpInLevel).isEqualTo(0L)
        assertThat(result.hero.stats.strength).isEqualTo(3)
    }

    private fun baseHero(
        progression: Progression,
        stats: HeroStats,
    ): Hero =
        Hero(
            id = "player",
            name = "Adventurer",
            stats = stats,
            progression = progression,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
}

