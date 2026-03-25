package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.ExplorationTileDao
import com.github.arhor.journey.data.local.db.entity.ExploredTileEntity
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
                ExploredTileEntity(
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
    fun `getExploredTiles should map dao range entities to canonical tiles when queried`() = runTest {
        // Given
        val dao = FakeExplorationTileDao(
            observedItems = listOf(
                ExploredTileEntity(
                    zoom = 16,
                    x = 34567,
                    y = 22345,
                ),
            ),
        )
        val subject = RoomExplorationTileRepository(dao = dao)

        // When
        val actual = subject.getExploredTiles(
            range = ExplorationTileRange(
                zoom = 16,
                minX = 34567,
                maxX = 34567,
                minY = 22345,
                maxY = 22345,
            ),
        )

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
    fun `getPackedExploredTile should delegate coordinates and return packed result`() = runTest {
        // Given
        val expectedPacked = 123L
        val dao = FakeExplorationTileDao(
            observedItems = emptyList(),
            packedByCoordinates = expectedPacked,
        )
        val subject = RoomExplorationTileRepository(dao = dao)
        val tile = ExplorationTile(zoom = 16, x = 100, y = 200)

        // When
        val actual = subject.getPackedExploredTile(tile = tile)

        // Then
        actual shouldBe expectedPacked
        dao.lastGetPackedCoordinates shouldBe Triple(16, 100, 200)
    }

    @Test
    fun `getPackedExploredTiles should return long array for queried range`() = runTest {
        // Given
        val expectedPacked = listOf(12L, 34L, 56L)
        val dao = FakeExplorationTileDao(
            observedItems = emptyList(),
            packedByRange = expectedPacked,
        )
        val subject = RoomExplorationTileRepository(dao = dao)
        val range = ExplorationTileRange(zoom = 16, minX = 1, maxX = 2, minY = 3, maxY = 4)

        // When
        val actual = subject.getPackedExploredTiles(range = range)

        // Then
        actual.toList() shouldBe expectedPacked
    }

    @Test
    fun `getPackedExploredTiles should return empty long array when range has no explored rows`() = runTest {
        // Given
        val dao = FakeExplorationTileDao(
            observedItems = emptyList(),
            packedByRange = emptyList(),
        )
        val subject = RoomExplorationTileRepository(dao = dao)

        // When
        val actual = subject.getPackedExploredTiles(
            range = ExplorationTileRange(zoom = 16, minX = 0, maxX = 1, minY = 0, maxY = 1),
        )

        // Then
        actual.isEmpty() shouldBe true
    }

    @Test
    fun `observePackedExploredTiles should emit long arrays from dao flow`() = runTest {
        // Given
        val dao = FakeExplorationTileDao(
            observedItems = emptyList(),
            observedPackedItems = listOf(77L, 88L),
        )
        val subject = RoomExplorationTileRepository(dao = dao)

        // When
        val actual = subject.observePackedExploredTiles(
            range = ExplorationTileRange(zoom = 16, minX = 0, maxX = 5, minY = 0, maxY = 5),
        ).first()

        // Then
        actual.toList() shouldBe listOf(77L, 88L)
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
            ExploredTileEntity(zoom = 16, x = 2, y = 1),
            ExploredTileEntity(zoom = 16, x = 1, y = 2),
            ExploredTileEntity(zoom = 16, x = 3, y = 2),
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
        private val observedItems: List<ExploredTileEntity>,
        private val packedByCoordinates: Long? = null,
        private val packedByRange: List<Long> = emptyList(),
        private val observedPackedItems: List<Long> = emptyList(),
    ) : ExplorationTileDao {
        val insertedEntities = mutableListOf<ExploredTileEntity>()
        var clearCalls: Int = 0
        var lastGetPackedCoordinates: Triple<Int, Int, Int>? = null

        override fun observeByRange(
            zoom: Int,
            minX: Int,
            maxX: Int,
            minY: Int,
            maxY: Int,
        ): Flow<List<ExploredTileEntity>> = flowOf(observedItems)

        override suspend fun getByRange(
            zoom: Int,
            minX: Int,
            maxX: Int,
            minY: Int,
            maxY: Int,
        ): List<ExploredTileEntity> = observedItems

        override suspend fun getPackedByCoordinates(
            zoom: Int,
            x: Int,
            y: Int,
        ): Long? {
            lastGetPackedCoordinates = Triple(zoom, x, y)
            return packedByCoordinates
        }

        override suspend fun getPackedByRange(
            zoom: Int,
            minX: Int,
            maxX: Int,
            minY: Int,
            maxY: Int,
        ): List<Long> = packedByRange

        override fun observePackedByRange(
            zoom: Int,
            minX: Int,
            maxX: Int,
            minY: Int,
            maxY: Int,
        ): Flow<List<Long>> = flowOf(observedPackedItems)

        override suspend fun insert(entities: List<ExploredTileEntity>): List<Long> {
            insertedEntities += entities
            return List(entities.size) { 1L }
        }

        override suspend fun clear() {
            clearCalls += 1
        }
    }
}
