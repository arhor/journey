package com.github.arhor.journey.domain.progression

import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroStats
import com.github.arhor.journey.domain.model.Progression
import com.github.arhor.journey.domain.model.Reward
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant

class ProgressionEngineTest {

    private val engine = ProgressionEngine(policy = ProgressionPolicy())

    @Test
    fun `applyReward should level up and grant level-up stats when xp crosses first threshold`() {
        // Given
        val hero = baseHero(
            progression = Progression(level = 1, xpInLevel = 990L),
            stats = HeroStats(strength = 1, vitality = 1, dexterity = 1, stamina = 1),
        )

        // When
        val result = engine.applyReward(
            hero = hero,
            reward = Reward(xp = 20L),
            now = Instant.parse("2026-01-01T00:10:00Z"),
        )

        // Then
        result.levelUps shouldBe 1
        result.hero.progression.level shouldBe 2
        result.hero.progression.xpInLevel shouldBe 10L
        result.hero.stats.strength shouldBe 2
        result.hero.stats.vitality shouldBe 2
        result.hero.stats.dexterity shouldBe 2
        result.hero.stats.stamina shouldBe 2
    }

    @Test
    fun `applyReward should level up multiple times when reward xp crosses multiple thresholds`() {
        // Given
        val hero = baseHero(
            progression = Progression(level = 1, xpInLevel = 0L),
            stats = HeroStats(strength = 1, vitality = 1, dexterity = 1, stamina = 1),
        )

        // When
        val result = engine.applyReward(
            hero = hero,
            reward = Reward(xp = 3000L),
            now = Instant.parse("2026-01-01T00:10:00Z"),
        )

        // Then
        result.levelUps shouldBe 2
        result.hero.progression.level shouldBe 3
        result.hero.progression.xpInLevel shouldBe 0L
        result.hero.stats.strength shouldBe 3
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
