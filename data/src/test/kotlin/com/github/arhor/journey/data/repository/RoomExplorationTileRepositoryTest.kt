package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.ExplorationTileDao
import com.github.arhor.journey.data.local.db.entity.ExplorationTileEntity
import com.github.arhor.journey.domain.model.ExplorationTile
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
    fun `observeExploredTiles should map dao range entities to canonical tiles when queried`() = runTest {
        // Given
        val dao = FakeExplorationTileDao(
            observedItems = listOf(
                ExplorationTileEntity(
                    zoom = 16,
                    x = 34567,
                    y = 22345,
                ),
            ),
        )
        val subject = RoomExplorationTileRepository(dao = dao)

        // When
        val actual = subject.observeExploredTiles(
            range = ExplorationTileRange(
                zoom = 16,
                minX = 34567,
                maxX = 34567,
                minY = 22345,
                maxY = 22345,
            ),
        ).first()

        // Then
        actual shouldBe setOf(
            ExplorationTile(
                zoom = 16,
                x = 34567,
                y = 22345,
            ),
        )
    }

    @Test
    fun `markExplored should persist sorted canonical tile entities when new tiles are provided`() = runTest {
        // Given
        val dao = FakeExplorationTileDao(observedItems = emptyList())
        val subject = RoomExplorationTileRepository(dao = dao)

        // When
        subject.markExplored(
            tiles = setOf(
                ExplorationTile(zoom = 16, x = 3, y = 2),
                ExplorationTile(zoom = 16, x = 1, y = 2),
                ExplorationTile(zoom = 16, x = 2, y = 1),
            ),
        )

        // Then
        dao.insertedEntities shouldContainExactly listOf(
            ExplorationTileEntity(zoom = 16, x = 2, y = 1),
            ExplorationTileEntity(zoom = 16, x = 1, y = 2),
            ExplorationTileEntity(zoom = 16, x = 3, y = 2),
        )
    }

    @Test
    fun `clear should delegate to dao when prototype state is reset`() = runTest {
        // Given
        val dao = FakeExplorationTileDao(observedItems = emptyList())
        val subject = RoomExplorationTileRepository(dao = dao)

        // When
        subject.clear()

        // Then
        dao.clearCalls shouldBe 1
    }

    private class FakeExplorationTileDao(
        private val observedItems: List<ExplorationTileEntity>,
    ) : ExplorationTileDao {
        val insertedEntities = mutableListOf<ExplorationTileEntity>()
        var clearCalls: Int = 0

        override fun observeByRange(
            zoom: Int,
            minX: Int,
            maxX: Int,
            minY: Int,
            maxY: Int,
        ): Flow<List<ExplorationTileEntity>> = flowOf(observedItems)

        override suspend fun insert(entities: List<ExplorationTileEntity>): List<Long> {
            insertedEntities += entities
            return List(entities.size) { 1L }
        }

        override suspend fun clear() {
            clearCalls += 1
        }
    }
}
