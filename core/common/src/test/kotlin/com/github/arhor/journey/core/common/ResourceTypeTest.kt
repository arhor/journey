package com.github.arhor.journey.core.common

import io.kotest.matchers.shouldBe
import org.junit.Test

class ResourceTypeTest {

    @Test
    fun `all should expose known resource types in display order`() {
        // Given
        val expected = listOf(
            ResourceType.WOOD,
            ResourceType.COAL,
            ResourceType.STONE,
        )

        // When
        val actual = ResourceType.all

        // Then
        actual shouldBe expected
    }

    @Test
    fun `fromTypeId should resolve known resource types`() {
        // When
        val actual = ResourceType.fromTypeId("coal")

        // Then
        actual shouldBe ResourceType.COAL
    }

    @Test
    fun `fromTypeId should return null for unknown resource types`() {
        // When
        val actual = ResourceType.fromTypeId("ore")

        // Then
        actual shouldBe null
    }
}
