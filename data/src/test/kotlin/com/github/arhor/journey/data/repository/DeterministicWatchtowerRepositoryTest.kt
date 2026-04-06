package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.WatchtowerStateDao
import com.github.arhor.journey.data.local.db.entity.WatchtowerStateEntity
import com.github.arhor.journey.domain.internal.WatchtowerGeneration
import com.github.arhor.journey.domain.internal.bounds
import com.github.arhor.journey.domain.internal.tileAt
import com.github.arhor.journey.domain.internal.tileRange
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.WatchtowerDefinition
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class DeterministicWatchtowerRepositoryTest {

    @Test
    fun `observeInBounds should compose generated definitions with sparse observed state rows`() = runTest {
        // Given
        val definition = occupiedDefinition()
        val dao = FakeWatchtowerStateDao(
            stateRows = mutableMapOf(
                definition.id to watchtowerStateEntity(
                    watchtowerId = definition.id,
                    claimedAt = Instant.parse("2026-04-01T10:00:00Z"),
                    level = 2,
                ),
            ),
        )
        val subject = DeterministicWatchtowerRepository(dao)

        // When
        val actual = subject.observeInBounds(bounds(tileFor(definition)))
            .first()

        // Then
        actual shouldHaveSize 1
        actual.single().definition shouldBe definition
        actual.single().state?.level shouldBe 2
        dao.observedIdRequests.single() shouldBe listOf(definition.id)
    }

    @Test
    fun `getIntersectingTiles should dedupe generator cells before loading sparse state`() = runTest {
        // Given
        val definition = occupiedDefinition()
        val cell = tileFor(definition)
        val sameCellChild = MapTile(
            zoom = WatchtowerGeneration.GENERATOR_TILE_ZOOM + 1,
            x = cell.x shl 1,
            y = cell.y shl 1,
        )
        val dao = FakeWatchtowerStateDao()
        val subject = DeterministicWatchtowerRepository(dao)

        // When
        val actual = subject.getIntersectingTiles(
            tiles = setOf(
                cell,
                sameCellChild,
            ),
        )

        // Then
        actual.map { it.definition.id } shouldBe listOf(definition.id)
        dao.getByIdsRequests.single() shouldBe listOf(definition.id)
    }

    @Test
    fun `getById should overlay sparse state and reject obsolete seeded ids`() = runTest {
        // Given
        val definition = occupiedDefinition()
        val dao = FakeWatchtowerStateDao(
            stateRows = mutableMapOf(
                definition.id to watchtowerStateEntity(
                    watchtowerId = definition.id,
                    claimedAt = null,
                    level = 0,
                ),
            ),
        )
        val subject = DeterministicWatchtowerRepository(dao)

        // When
        val actual = subject.getById(definition.id)
        val obsolete = subject.getById("gdansk-neptune")

        // Then
        actual?.definition shouldBe definition
        actual?.state?.watchtowerId shouldBe definition.id
        obsolete.shouldBeNull()
    }

    @Test
    fun `getInBounds should stay bounded for representative areas`() = runTest {
        // Given
        val queryBounds = GeoBounds(
            south = 54.18,
            west = 18.32,
            north = 54.48,
            east = 18.88,
        )
        val dao = FakeWatchtowerStateDao()
        val subject = DeterministicWatchtowerRepository(dao)
        val cellRange = tileRange(
            bounds = queryBounds,
            zoom = WatchtowerGeneration.GENERATOR_TILE_ZOOM,
        )

        // When
        val actual = subject.getInBounds(queryBounds)

        // Then
        (actual.size <= cellRange.tileCount.toInt()) shouldBe true
        dao.getByIdsRequests.single().size shouldBe actual.size
    }

    @Test
    fun `markDiscovered should return false when the id is not a valid deterministic watchtower`() = runTest {
        // Given
        val dao = FakeWatchtowerStateDao()
        val subject = DeterministicWatchtowerRepository(dao)

        // When
        val actual = subject.markDiscovered(
            id = "legacy-seeded-watchtower",
            discoveredAt = Instant.parse("2026-04-01T10:00:00Z"),
        )

        // Then
        actual shouldBe false
        dao.insertedStates shouldHaveSize 0
    }

    @Test
    fun `getInBounds should chunk sparse state lookups when candidate ids exceed sqlite bind limits`() = runTest {
        // Given
        val queryBounds = bounds(
            ExplorationTileRange(
                zoom = WatchtowerGeneration.GENERATOR_TILE_ZOOM,
                minX = 0,
                maxX = 63,
                minY = 0,
                maxY = 63,
            ),
        )
        val dao = FakeWatchtowerStateDao()
        val subject = DeterministicWatchtowerRepository(dao)

        // When
        val actual = subject.getInBounds(queryBounds)

        // Then
        actual.isNotEmpty() shouldBe true
        dao.getByIdsRequests.size shouldBe 2
        dao.getByIdsRequests.sumOf(List<String>::size) shouldBe actual.size
    }

    private fun occupiedDefinition(): WatchtowerDefinition = searchCells { cell ->
        WatchtowerGeneration.definitionForCell(cell)
    }

    private fun tileFor(definition: WatchtowerDefinition): MapTile =
        tileAt(
            point = definition.location,
            zoom = WatchtowerGeneration.GENERATOR_TILE_ZOOM,
        )

    private fun searchCells(
        transform: (MapTile) -> WatchtowerDefinition?,
    ): WatchtowerDefinition {
        for (y in 10_000..10_128) {
            for (x in 10_000..10_128) {
                val cell = MapTile(
                    zoom = WatchtowerGeneration.GENERATOR_TILE_ZOOM,
                    x = x,
                    y = y,
                )
                transform(cell)?.let { definition ->
                    return definition
                }
            }
        }

        error("Expected to find an occupied deterministic watchtower cell in the test search window")
    }

    private fun watchtowerStateEntity(
        watchtowerId: String,
        claimedAt: Instant?,
        level: Int,
    ) = WatchtowerStateEntity(
        watchtowerId = watchtowerId,
        discoveredAt = Instant.parse("2026-03-31T08:00:00Z"),
        claimedAt = claimedAt,
        level = level,
        updatedAt = Instant.parse("2026-03-31T08:00:00Z"),
    )

    private class FakeWatchtowerStateDao(
        private val stateRows: MutableMap<String, WatchtowerStateEntity> = linkedMapOf(),
        private val insertStateResult: Long = 1L,
    ) : WatchtowerStateDao {
        val observedIdRequests = mutableListOf<List<String>>()
        val getByIdsRequests = mutableListOf<List<String>>()
        val insertedStates = mutableListOf<WatchtowerStateEntity>()

        override fun observeByIds(ids: List<String>): Flow<List<WatchtowerStateEntity>> {
            observedIdRequests += ids
            return flowOf(ids.mapNotNull(stateRows::get))
        }

        override suspend fun getByIds(ids: List<String>): List<WatchtowerStateEntity> {
            getByIdsRequests += ids
            return ids.mapNotNull(stateRows::get)
        }

        override suspend fun getById(id: String): WatchtowerStateEntity? = stateRows[id]

        override suspend fun insertState(entity: WatchtowerStateEntity): Long {
            insertedStates += entity
            if (insertStateResult != -1L) {
                stateRows.putIfAbsent(entity.watchtowerId, entity)
            }
            return insertStateResult
        }

        override suspend fun markClaimed(
            watchtowerId: String,
            claimedAt: Instant,
            level: Int,
            updatedAt: Instant,
        ): Int = 1

        override suspend fun setLevel(
            watchtowerId: String,
            level: Int,
            updatedAt: Instant,
        ): Int = 1
    }
}
