package com.github.arhor.journey.domain.internal

import com.github.arhor.journey.core.testing.FakeH3Grid
import com.github.arhor.journey.domain.model.GeoPoint
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

class BreachNodeGenerationTest {

    @Test
    fun `definitionForCell should return stable definition when cell is occupied`() {
        // Given
        val h3 = FakeH3Grid.withRepeatedCells()
        val cellId = OCCUPIED_CELL_ID

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
        val cellId = OCCUPIED_CELL_ID

        // When
        val actual = BreachNodeGeneration.definitionForCell(cellId, h3)

        // Then
        actual.shouldNotBeNull()
        actual.id shouldBe "breach-node:v1:h3r9:$cellId"
    }

    @Test
    fun `definitionForCell should return null when cell resolution does not match breach resolution`() {
        // Given
        val h3 = FakeH3Grid(
            resolutions = mapOf(OCCUPIED_CELL_ID to BreachBalance.H3_RESOLUTION - 1),
        )

        // When
        val actual = BreachNodeGeneration.definitionForCell(OCCUPIED_CELL_ID, h3)

        // Then
        actual.shouldBeNull()
    }

    @Test
    fun `definitionForCell should return null when cell is unoccupied`() {
        // Given
        val h3 = FakeH3Grid.withRepeatedCells()

        // When
        val actual = BreachNodeGeneration.definitionForCell(UNOCCUPIED_CELL_ID, h3)

        // Then
        actual.shouldBeNull()
    }

    @Test
    fun `definitionsForCells should return a single definition when duplicate occupied cells are provided`() {
        // Given
        val h3 = FakeH3Grid.withRepeatedCells()
        val input = listOf(OCCUPIED_CELL_ID, OCCUPIED_CELL_ID, UNOCCUPIED_CELL_ID)

        // When
        val actual = BreachNodeGeneration.definitionsForCells(cellIds = input, h3Grid = h3)

        // Then
        actual.map { definition -> definition.id } shouldContainExactly listOf(
            "breach-node:v1:h3r9:$OCCUPIED_CELL_ID",
        )
    }

    @Test
    fun `definitionForCell should place location between center and boundary when boundary is explicit`() {
        // Given
        val center = GeoPoint(lat = 50.45, lon = 30.52)
        val boundary = GeoPoint(lat = 50.46, lon = 30.53)
        val h3 = FakeH3Grid(
            disk = listOf(OCCUPIED_CELL_ID),
            centers = mapOf(OCCUPIED_CELL_ID to center),
            boundaries = mapOf(OCCUPIED_CELL_ID to listOf(boundary)),
            resolutions = mapOf(OCCUPIED_CELL_ID to BreachBalance.H3_RESOLUTION),
        )

        // When
        val actual = BreachNodeGeneration.definitionForCell(OCCUPIED_CELL_ID, h3)

        // Then
        actual.shouldNotBeNull()
        actual.location.lat shouldBeGreaterThan center.lat
        actual.location.lat shouldBeLessThan boundary.lat
        actual.location.lon shouldBeGreaterThan center.lon
        actual.location.lon shouldBeLessThan boundary.lon
    }

    private companion object {
        const val OCCUPIED_CELL_ID = "cell-6"
        const val UNOCCUPIED_CELL_ID = "cell-0"
    }
}
