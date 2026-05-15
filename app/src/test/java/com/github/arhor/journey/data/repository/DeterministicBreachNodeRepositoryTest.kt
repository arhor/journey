package com.github.arhor.journey.data.repository

import com.github.arhor.journey.core.testing.FakeH3Grid
import com.github.arhor.journey.domain.model.BreachNodeState
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.data.local.db.dao.BreachNodeStateDao
import com.github.arhor.journey.data.local.db.entity.BreachNodeStateEntity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class DeterministicBreachNodeRepositoryTest {

    @Test
    fun `getInBounds should compose generated definitions with persisted states when query window includes occupied cell`() =
        runTest {
            // Given
            val center = GeoPoint(lat = 50.45, lon = 30.52)
            val cellId = OCCUPIED_CELL_ID
            val h3Grid = FakeH3Grid(
                cellsInBounds = listOf(cellId),
                centers = mapOf(cellId to center),
            )
            val dao = FakeBreachNodeStateDao(
                states = listOf(
                    breachNodeStateEntity(
                        breachNodeId = "breach-node:v1:h3r9:$cellId",
                        h3CellId = cellId,
                        discoveredAt = Instant.parse("2026-05-15T10:00:00Z"),
                    ),
                ),
            )
            val subject = DeterministicBreachNodeRepository(
                dao = dao,
                h3Grid = h3Grid,
            )

            // When
            val actual = subject.getInBounds(boundsAround(center))

            // Then
            actual.size shouldBe 1
            actual.single().definition.h3CellId shouldBe cellId
            actual.single().state shouldBe BreachNodeState(
                breachNodeId = "breach-node:v1:h3r9:$cellId",
                h3CellId = cellId,
                discoveredAt = Instant.parse("2026-05-15T10:00:00Z"),
                controlledAt = null,
                lockdownUntil = null,
                updatedAt = Instant.parse("2026-05-15T10:00:00Z"),
            )
        }

    @Test
    fun `getById should resolve a generated breach record when state is absent`() = runTest {
        // Given
        val cellId = OCCUPIED_CELL_ID
        val h3Grid = FakeH3Grid(
            centers = mapOf(cellId to GeoPoint(lat = 50.45, lon = 30.52)),
        )
            val dao = FakeBreachNodeStateDao(states = emptyList())
        val subject = DeterministicBreachNodeRepository(
            dao = dao,
            h3Grid = h3Grid,
        )

        // When
        val actual = subject.getById("breach-node:v1:h3r9:$cellId")

        // Then
        actual?.definition?.h3CellId shouldBe cellId
        actual?.state shouldBe null
    }

    @Test
    fun `observeControlledCells should emit controlled cells that fall within the requested bounds`() =
        runTest {
            // Given
            val controlledCell = OCCUPIED_CELL_ID
            val uncontrolledCell = "cell-8"
            val center = GeoPoint(lat = 50.45, lon = 30.52)
            val h3Grid = FakeH3Grid(
                cellsInBounds = listOf(controlledCell, uncontrolledCell),
                centers = mapOf(
                    controlledCell to center,
                    uncontrolledCell to center.copy(lat = center.lat + 0.01),
                ),
            )
            val dao = FakeBreachNodeStateDao(
                states = listOf(
                    breachNodeStateEntity(
                        breachNodeId = "breach-node:v1:h3r9:$controlledCell",
                        h3CellId = controlledCell,
                        controlledAt = Instant.parse("2026-05-15T10:05:00Z"),
                    ),
                    breachNodeStateEntity(
                        breachNodeId = "breach-node:v1:h3r9:$uncontrolledCell",
                        h3CellId = uncontrolledCell,
                    ),
                ),
            )
            val subject = DeterministicBreachNodeRepository(
                dao = dao,
                h3Grid = h3Grid,
            )

            // When
            val actual = subject.observeControlledCells(boundsAround(center)).first()

            // Then
            actual shouldBe setOf(controlledCell)
        }

    @Test
    fun `markControlled should update the stored breach state when a known node is provided`() =
        runTest {
            // Given
            val cellId = OCCUPIED_CELL_ID
            val updatedAt = Instant.parse("2026-05-15T10:10:00Z")
            val controlledAt = Instant.parse("2026-05-15T10:09:00Z")
            val dao = FakeBreachNodeStateDao(
                states = listOf(
                    breachNodeStateEntity(
                        breachNodeId = "breach-node:v1:h3r9:$cellId",
                        h3CellId = cellId,
                        discoveredAt = Instant.parse("2026-05-15T10:00:00Z"),
                    ),
                ),
            )
            val subject = DeterministicBreachNodeRepository(
                dao = dao,
                h3Grid = FakeH3Grid(centers = mapOf(cellId to GeoPoint(lat = 50.45, lon = 30.52))),
            )

            // When
            val actual = subject.markControlled(
                id = "breach-node:v1:h3r9:$cellId",
                h3CellId = cellId,
                controlledAt = controlledAt,
                updatedAt = updatedAt,
            )

            // Then
            actual shouldBe true
            dao.states.single().controlledAt shouldBe controlledAt
            dao.states.single().updatedAt shouldBe updatedAt
        }

    private fun boundsAround(center: GeoPoint): GeoBounds =
        GeoBounds(
            south = center.lat - 0.01,
            west = center.lon - 0.01,
            north = center.lat + 0.01,
            east = center.lon + 0.01,
        )

    private fun breachNodeStateEntity(
        breachNodeId: String,
        h3CellId: String,
        discoveredAt: Instant? = null,
        controlledAt: Instant? = null,
        lockdownUntil: Instant? = null,
        updatedAt: Instant = discoveredAt ?: controlledAt ?: Instant.parse("2026-05-15T10:00:00Z"),
    ): BreachNodeStateEntity =
        BreachNodeStateEntity(
            breachNodeId = breachNodeId,
            h3CellId = h3CellId,
            discoveredAt = discoveredAt,
            controlledAt = controlledAt,
            lockdownUntil = lockdownUntil,
            updatedAt = updatedAt,
        )

    private class FakeBreachNodeStateDao(
        states: List<BreachNodeStateEntity>,
    ) : BreachNodeStateDao {
        val states: MutableList<BreachNodeStateEntity> = states.toMutableList()

        override fun observeByCellIds(h3CellIds: Collection<String>): Flow<List<BreachNodeStateEntity>> =
            flowOf(
                states.filter { it.h3CellId in h3CellIds },
            )

        override suspend fun getByCellIds(h3CellIds: Collection<String>): List<BreachNodeStateEntity> =
            states.filter { it.h3CellId in h3CellIds }

        override suspend fun getById(id: String): BreachNodeStateEntity? =
            states.firstOrNull { it.breachNodeId == id }

        override suspend fun getByH3CellId(h3CellId: String): BreachNodeStateEntity? =
            states.firstOrNull { it.h3CellId == h3CellId }

        override suspend fun upsert(entity: BreachNodeStateEntity) {
            states.removeAll { it.breachNodeId == entity.breachNodeId }
            states.add(entity)
        }

        override suspend fun markControlled(
            id: String,
            h3CellId: String,
            controlledAt: Instant,
            updatedAt: Instant,
        ): Int {
            val index = states.indexOfFirst { it.breachNodeId == id && it.h3CellId == h3CellId }
            if (index == -1) {
                return 0
            }

            val current = states[index]
            states[index] = current.copy(
                controlledAt = controlledAt,
                updatedAt = updatedAt,
            )
            return 1
        }

        override fun observeControlledCellIdsByCellIds(h3CellIds: Collection<String>): Flow<List<String>> =
            flowOf(
                states
                    .asSequence()
                    .filter { it.h3CellId in h3CellIds && it.controlledAt != null }
                    .map { it.h3CellId }
                    .distinct()
                    .toList(),
            )
    }

    private companion object {
        const val OCCUPIED_CELL_ID = "cell-6"
    }
}
