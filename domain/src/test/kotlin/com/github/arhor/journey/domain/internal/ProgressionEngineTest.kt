package com.github.arhor.journey.domain.internal

import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroStats
import com.github.arhor.journey.domain.model.Progression
import com.github.arhor.journey.domain.model.Reward
import com.github.arhor.journey.domain.model.StatsDelta
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant

class ProgressionEngineTest {

    private val subject = ProgressionEngine(policy = ProgressionPolicy())

    @Test
    fun `applyReward should level up twice and clamp initial invalid progression when reward grants enough xp`() {
        // Given
        val now = Instant.parse("2026-02-01T10:00:00Z")
        val hero = hero(
            level = 0,
            xpInLevel = -50L,
            stats = HeroStats(strength = 1, vitality = 1, dexterity = 1, stamina = 1),
        )
        val reward = Reward(
            xp = 3_200L,
            stats = StatsDelta(strength = -5, vitality = 2, dexterity = 0, stamina = 1),
        )

        // When
        val result = subject.applyReward(hero = hero, reward = reward, now = now)

        // Then
        result.levelUps shouldBe 2
        result.levelUpBonus shouldBe StatsDelta(strength = 2, vitality = 2, dexterity = 2, stamina = 2)
        result.hero.progression shouldBe Progression(level = 3, xpInLevel = 200L)
        result.hero.stats shouldBe HeroStats(strength = 2, vitality = 5, dexterity = 3, stamina = 4)
        result.hero.updatedAt shouldBe now
    }

    @Test
    fun `applyReward should ignore negative xp and clamp stats when reward reduces attributes below zero`() {
        // Given
        val now = Instant.parse("2026-02-01T10:00:00Z")
        val hero = hero(
            level = 2,
            xpInLevel = 100L,
            stats = HeroStats(strength = 2, vitality = 1, dexterity = 0, stamina = 4),
        )
        val reward = Reward(
            xp = -500L,
            stats = StatsDelta(strength = -10, vitality = -1, dexterity = -1, stamina = -10),
        )

        // When
        val result = subject.applyReward(hero = hero, reward = reward, now = now)

        // Then
        result.levelUps shouldBe 0
        result.levelUpBonus shouldBe StatsDelta()
        result.hero.progression shouldBe Progression(level = 2, xpInLevel = 100L)
        result.hero.stats shouldBe HeroStats(strength = 0, vitality = 0, dexterity = 0, stamina = 0)
        result.hero.updatedAt shouldBe now
    }

    private fun hero(
        level: Int,
        xpInLevel: Long,
        stats: HeroStats = HeroStats(strength = 5, vitality = 5, dexterity = 5, stamina = 5),
    ): Hero {
        val createdAt = Instant.parse("2026-01-01T00:00:00Z")

        return Hero(
            id = "hero-1",
            name = "Hero",
            stats = stats,
            progression = Progression(level = level, xpInLevel = xpInLevel),
            createdAt = createdAt,
            updatedAt = createdAt,
        )
    }
}
