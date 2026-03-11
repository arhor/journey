package com.github.arhor.journey.domain.model

import com.github.arhor.journey.domain.player.model.HeroEnergy
import io.kotest.matchers.shouldBe
import org.junit.Test

class HeroEnergyTest {

    @Test
    fun `invoke should clamp max to one when max is less than one`() {
        // Given
        val current = 5
        val max = 0

        // When
        val energy = HeroEnergy(current = current, max = max)

        // Then
        energy.max shouldBe 1
        energy.current shouldBe 1
    }

    @Test
    fun `invoke should clamp current to zero when current is negative`() {
        // Given
        val current = -20
        val max = 50

        // When
        val energy = HeroEnergy(current = current, max = max)

        // Then
        energy.current shouldBe 0
        energy.max shouldBe 50
    }

    @Test
    fun `invoke should clamp current to max when current exceeds max`() {
        // Given
        val current = 250
        val max = 100

        // When
        val energy = HeroEnergy(current = current, max = max)

        // Then
        energy.current shouldBe 100
        energy.max shouldBe 100
    }
}
