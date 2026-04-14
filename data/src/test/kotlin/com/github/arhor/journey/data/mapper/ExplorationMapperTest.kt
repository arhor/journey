package com.github.arhor.journey.data.mapper

import com.github.arhor.journey.data.local.db.entity.ExploredTileEntity
import com.github.arhor.journey.domain.model.MapTile
import io.kotest.matchers.shouldBe
import org.junit.Test

class ExplorationMapperTest {

    @Test
    fun `toDomain should map exploration tile entity coordinates when prototype tile is provided`() {
        // Given
        val entity = ExploredTileEntity(
            zoom = 16,
            x = 34567,
            y = 22345,
        )

        // When
        val actual = entity.toDomain()

        // Then
        actual shouldBe MapTile(
            zoom = 16,
            x = 34567,
            y = 22345,
        )
    }

    @Test
    fun `toEntity should map exploration tile coordinates when prototype tile is provided`() {
        // Given
        val tile = MapTile(
            zoom = 16,
            x = 34567,
            y = 22345,
        )

        // When
        val actual = tile.toEntity()

        // Then
        actual shouldBe ExploredTileEntity(
            zoom = 16,
            x = 34567,
            y = 22345,
        )
    }
}
