package com.github.arhor.journey.domain.internal

import io.kotest.matchers.shouldBe
import org.junit.Test

class ProgressionPolicyTest {

    private val subject = ProgressionPolicy()

    @Test
    fun `xpToNextLevel should return linear requirement when level is positive`() {
        // Given
        val level = 4

        // When
        val result = subject.xpToNextLevel(level)

        // Then
        result shouldBe 4_000L
    }

    @Test
    fun `xpToNextLevel should clamp to level one requirement when level is zero or negative`() {
        // Given
        val level = -10

        // When
        val result = subject.xpToNextLevel(level)

        // Then
        result shouldBe 1_000L
    }
}
