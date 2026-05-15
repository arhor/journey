package com.github.arhor.journey.domain.internal

import com.github.arhor.journey.core.testing.FakeH3Grid
import com.github.arhor.journey.domain.spatial.H3Grid
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test

class BreachNodeGenerationTest {

    @Test
    fun `definitionForCell should return stable definition when cell is occupied`() {
        // Given
        val h3 = FakeH3Grid.withRepeatedCells()
        val cellId = firstOccupiedCell(h3)

        // When
        val first = BreachNodeGeneration.definitionForCell(cellId, h3)
        val second = BreachNodeGeneration.definitionForCell(cellId, h3)

        // Then
        first shouldBe second
    }

    @Test
    fun `definitionForCell should use h3 cell id in stable breach id when cell is occupied`() {
        // Given
        val h3 = FakeH3Grid.withRepeatedCells()
        val cellId = firstOccupiedCell(h3)

        // When
        val actual = BreachNodeGeneration.definitionForCell(cellId, h3)

        // Then
        actual?.id shouldBe "breach-node:v1:h3r9:$cellId"
    }

    @Test
    fun `definitionsForCells should return deterministic order sorted by id when generated definitions are present`() {
        // Given
        val h3 = FakeH3Grid.withRepeatedCells()
        val first = firstOccupiedCell(h3)
        val second = nextOccupiedCell(afterCellId = first, h3 = h3)
        val input = listOf(second, first)

        // When
        val actual = BreachNodeGeneration.definitionsForCells(cellIds = input, h3Grid = h3)

        // Then
        actual.map { definition -> definition.id } shouldContainExactly listOf(
            "breach-node:v1:h3r9:$first",
            "breach-node:v1:h3r9:$second",
        ).sorted()
    }

    private fun firstOccupiedCell(h3: H3Grid): String =
        candidateCells().first { cellId -> BreachNodeGeneration.definitionForCell(cellId = cellId, h3Grid = h3) != null }

    private fun nextOccupiedCell(afterCellId: String, h3: H3Grid): String =
        candidateCells()
            .dropWhile { cellId -> cellId != afterCellId }
            .drop(1)
            .first { cellId -> BreachNodeGeneration.definitionForCell(cellId = cellId, h3Grid = h3) != null }

    private fun candidateCells(): Sequence<String> =
        generateSequence(0) { index -> index + 1 }
            .map { index -> "cell-$index" }
}
