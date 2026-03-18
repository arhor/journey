package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.ExplorationTileDao
import com.github.arhor.journey.data.local.db.entity.ExplorationTileEntity
import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileLight
import com.github.arhor.journey.domain.model.ExplorationTileRange
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomExplorationTileRepositoryTest {

    @Test
    fun `observeExplorationTileLights should map dao range entities to canonical tile lights when queried`() = runTest {
        // Given
        val dao = FakeExplorationTileDao(
            initialItems = listOf(
                ExplorationTileEntity(
                    zoom = 16,
                    x = 34567,
                    y = 22345,
                    light = 0.66f,
                ),
            ),
        )
        val subject = RoomExplorationTileRepository(dao = dao)

        // When
        val actual = subject.observeExplorationTileLights(
            range = ExplorationTileRange(
                zoom = 16,
                minX = 34567,
                maxX = 34567,
                minY = 22345,
                maxY = 22345,
            ),
        ).first()

        // Then
        actual shouldContainExactly listOf(
            ExplorationTileLight(
                tile = ExplorationTile(
                    zoom = 16,
                    x = 34567,
                    y = 22345,
                ),
                light = 0.66f,
            ),
        )
    }

    @Test
    fun `accumulateExplorationTileLights should persist sorted canonical tile entities when new tile lights are provided`() = runTest {
        // Given
        val dao = FakeExplorationTileDao(initialItems = emptyList())
        val subject = RoomExplorationTileRepository(dao = dao)

        // When
        subject.accumulateExplorationTileLights(
            tileLights = listOf(
                ExplorationTileLight(
                    tile = ExplorationTile(zoom = 16, x = 3, y = 2),
                    light = 0.33f,
                ),
                ExplorationTileLight(
                    tile = ExplorationTile(zoom = 16, x = 1, y = 2),
                    light = 0.66f,
                ),
                ExplorationTileLight(
                    tile = ExplorationTile(zoom = 16, x = 2, y = 1),
                    light = 1.0f,
                ),
            ),
        )

        // Then
        dao.accumulatedEntities shouldContainExactly listOf(
            ExplorationTileEntity(zoom = 16, x = 2, y = 1, light = 1.0f),
            ExplorationTileEntity(zoom = 16, x = 1, y = 2, light = 0.66f),
            ExplorationTileEntity(zoom = 16, x = 3, y = 2, light = 0.33f),
        )
    }

    @Test
    fun `accumulateExplorationTileLights should keep the brighter stored light when weaker light is written later`() = runTest {
        // Given
        val dao = FakeExplorationTileDao(
            initialItems = listOf(
                ExplorationTileEntity(
                    zoom = 16,
                    x = 34567,
                    y = 22345,
                    light = 0.66f,
                ),
            ),
        )
        val subject = RoomExplorationTileRepository(dao = dao)
        val tile = ExplorationTile(zoom = 16, x = 34567, y = 22345)

        // When
        subject.accumulateExplorationTileLights(
            tileLights = listOf(
                ExplorationTileLight(tile = tile, light = 0.33f),
            ),
        )
        subject.accumulateExplorationTileLights(
            tileLights = listOf(
                ExplorationTileLight(tile = tile, light = 1.0f),
            ),
        )

        // Then
        dao.storedItems.values.single() shouldBe ExplorationTileEntity(
            zoom = 16,
            x = 34567,
            y = 22345,
            light = 1.0f,
        )
    }

    @Test
    fun `accumulateExplorationTileLights should coalesce duplicate tiles in the same batch using the brightest light`() = runTest {
        // Given
        val dao = FakeExplorationTileDao(initialItems = emptyList())
        val subject = RoomExplorationTileRepository(dao = dao)
        val tile = ExplorationTile(zoom = 16, x = 34567, y = 22345)

        // When
        subject.accumulateExplorationTileLights(
            tileLights = listOf(
                ExplorationTileLight(tile = tile, light = 0.33f),
                ExplorationTileLight(tile = tile, light = 0.66f),
                ExplorationTileLight(tile = tile, light = 1.0f),
            ),
        )

        // Then
        dao.accumulatedEntities shouldContainExactly listOf(
            ExplorationTileEntity(
                zoom = 16,
                x = 34567,
                y = 22345,
                light = 1.0f,
            ),
        )
    }

    @Test
    fun `clearExplorationTileLights should delegate to dao when prototype state is reset`() = runTest {
        // Given
        val dao = FakeExplorationTileDao(initialItems = emptyList())
        val subject = RoomExplorationTileRepository(dao = dao)

        // When
        subject.clearExplorationTileLights()

        // Then
        dao.clearCalls shouldBe 1
    }

    private class FakeExplorationTileDao(
        initialItems: List<ExplorationTileEntity>,
    ) : ExplorationTileDao {
        val accumulatedEntities = mutableListOf<ExplorationTileEntity>()
        val storedItems = initialItems.associateBy(ExplorationTileEntity::toKey).toMutableMap()
        var clearCalls: Int = 0

        override fun observeByRange(
            zoom: Int,
            minX: Int,
            maxX: Int,
            minY: Int,
            maxY: Int,
        ): Flow<List<ExplorationTileEntity>> = flowOf(
            storedItems.values
                .filter { entity ->
                    entity.zoom == zoom &&
                        entity.x in minX..maxX &&
                        entity.y in minY..maxY
                }
                .sorted(),
        )

        override suspend fun insertOrAccumulate(
            zoom: Int,
            x: Int,
            y: Int,
            light: Float,
        ) {
            val incoming = ExplorationTileEntity(
                zoom = zoom,
                x = x,
                y = y,
                light = light,
            )
            accumulatedEntities += incoming

            val key = incoming.toKey()
            val existing = storedItems[key]
            storedItems[key] = if (existing == null) {
                incoming
            } else {
                existing.copy(light = maxOf(existing.light, incoming.light))
            }
        }

        override suspend fun clear() {
            clearCalls += 1
            storedItems.clear()
        }
    }
}

private fun ExplorationTileEntity.toKey(): String = "$zoom:$x:$y"
