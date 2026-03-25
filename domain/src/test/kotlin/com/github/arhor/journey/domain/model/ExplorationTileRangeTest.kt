package com.github.arhor.journey.domain.model

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import org.junit.Test

class ExplorationTileRangeTest {

    @Test
    fun `intersectionOrNull should return null when ranges do not overlap`() {
        // Given
        val subject = ExplorationTileRange(
            zoom = 16,
            minX = 10,
            maxX = 12,
            minY = 20,
            maxY = 22,
        )
        val other = ExplorationTileRange(
            zoom = 16,
            minX = 13,
            maxX = 15,
            minY = 20,
            maxY = 22,
        )

        // When
        val actual = subject.intersectionOrNull(other)

        // Then
        actual.shouldBeNull()
    }

    @Test
    fun `subtract should split outer range into non overlapping rectangles when inner range overlaps the center`() {
        // Given
        val subject = ExplorationTileRange(
            zoom = 16,
            minX = 10,
            maxX = 14,
            minY = 20,
            maxY = 24,
        )
        val other = ExplorationTileRange(
            zoom = 16,
            minX = 11,
            maxX = 13,
            minY = 21,
            maxY = 23,
        )

        // When
        val actual = subject.subtract(other)

        // Then
        actual shouldContainExactly listOf(
            ExplorationTileRange(
                zoom = 16,
                minX = 10,
                maxX = 14,
                minY = 20,
                maxY = 20,
            ),
            ExplorationTileRange(
                zoom = 16,
                minX = 10,
                maxX = 14,
                minY = 24,
                maxY = 24,
            ),
            ExplorationTileRange(
                zoom = 16,
                minX = 10,
                maxX = 10,
                minY = 21,
                maxY = 23,
            ),
            ExplorationTileRange(
                zoom = 16,
                minX = 14,
                maxX = 14,
                minY = 21,
                maxY = 23,
            ),
        )
    }
}
