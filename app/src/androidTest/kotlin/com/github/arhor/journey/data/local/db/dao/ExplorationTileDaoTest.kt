package com.github.arhor.journey.data.local.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.arhor.journey.data.local.db.JourneyDatabase
import com.github.arhor.journey.data.local.db.entity.ExploredTileEntity
import com.github.arhor.journey.domain.model.MapTile
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExplorationTileDaoTest {

    private lateinit var database: JourneyDatabase
    private lateinit var explorationTileDao: ExplorationTileDao

    @Before
    fun setUp() {
        runTest {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            database = Room.inMemoryDatabaseBuilder(context, JourneyDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            explorationTileDao = database.explorationTileDao()
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `getPackedByCoordinates should return packed tile when tile exists`() = runTest {
        // Given
        explorationTileDao.insert(
            entities = listOf(
                ExploredTileEntity(zoom = 16, x = 34567, y = 22345),
            ),
        )

        // When
        val actual = explorationTileDao.getPackedByCoordinates(
            zoom = 16,
            x = 34567,
            y = 22345,
        )

        // Then
        actual shouldBe MapTile.pack(
            zoom = 16,
            x = 34567,
            y = 22345,
        )
    }

    @Test
    fun `getPackedByCoordinates should mask zoom to 8 bits in SQL projection`() = runTest {
        // Given
        explorationTileDao.insert(
            entities = listOf(
                ExploredTileEntity(zoom = 0x1AB, x = 10, y = 20),
            ),
        )

        // When
        val actual = explorationTileDao.getPackedByCoordinates(
            zoom = 0x1AB,
            x = 10,
            y = 20,
        )

        // Then
        actual shouldBe MapTile.pack(
            zoom = 0xAB,
            x = 10,
            y = 20,
        )
        MapTile.unpackZoom(actual!!) shouldBe 0xAB
    }

    @Test
    fun `getPackedByRange should return deterministic packed ordering for range`() = runTest {
        // Given
        explorationTileDao.insert(
            entities = listOf(
                ExploredTileEntity(zoom = 16, x = 2, y = 3),
                ExploredTileEntity(zoom = 16, x = 1, y = 2),
                ExploredTileEntity(zoom = 16, x = 3, y = 2),
            ),
        )

        // When
        val actual = explorationTileDao.getPackedByRange(
            zoom = 16,
            minX = 1,
            maxX = 3,
            minY = 2,
            maxY = 3,
        )

        // Then
        actual shouldContainExactly listOf(
            MapTile.pack(zoom = 16, x = 1, y = 2),
            MapTile.pack(zoom = 16, x = 2, y = 3),
            MapTile.pack(zoom = 16, x = 3, y = 2),
        )
    }

    @Test
    fun `observePackedByRange should emit updated packed values when rows change`() = runTest {
        // Given
        val emissions = mutableListOf<List<Long>>()
        val firstEmissionReceived = kotlinx.coroutines.CompletableDeferred<Unit>()

        val collectJob = launch {
            explorationTileDao.observePackedByRange(
                zoom = 16,
                minX = 0,
                maxX = 10,
                minY = 0,
                maxY = 10,
            )
                .onEach { emission ->
                    emissions += emission
                    if (emissions.size == 1) {
                        firstEmissionReceived.complete(Unit)
                    }
                }
                .take(2)
                .collect()
        }

        firstEmissionReceived.await()

        // When
        explorationTileDao.insert(
            entities = listOf(
                ExploredTileEntity(zoom = 16, x = 5, y = 6),
            ),
        )
        collectJob.join()

        // Then
        emissions shouldContainExactly listOf(
            emptyList(),
            listOf(MapTile.pack(zoom = 16, x = 5, y = 6)),
        )
    }
}
