package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.BreachNodeDefinition
import com.github.arhor.journey.domain.model.BreachNodePhase
import com.github.arhor.journey.domain.model.BreachNodeRecord
import com.github.arhor.journey.domain.model.BreachNodeState
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.error.BreachNodeError
import com.github.arhor.journey.domain.repository.BreachNodeRepository
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BreachNodeUseCaseTest {

    @Test
    fun `invoke should persist discovery when actor is within interaction radius`() = runTest {
        // Given
        val clock = fixedClock()
        val actorLocation = GeoPoint(lat = 50.4500, lon = 30.5200)
        val record = record(
            id = "breach-node:v1:h3r9:cell-1",
            cellId = "cell-1",
            location = GeoPoint(lat = 50.4501, lon = 30.5201),
        )
        val repository = FakeBreachNodeRepository(records = listOf(record))
        val subject = DiscoverBreachNodeUseCase(repository, clock)

        // When
        val actual = subject(id = record.definition.id, actorLocation = actorLocation)

        // Then
        actual shouldBe Output.Success(
            BreachNodeState(
                breachNodeId = record.definition.id,
                h3CellId = record.definition.h3CellId,
                discoveredAt = FIXED_INSTANT,
                controlledAt = null,
                lockdownUntil = null,
                updatedAt = FIXED_INSTANT,
            ),
        )
        repository.discoveredCalls shouldContainExactly listOf(
            FakeBreachNodeRepository.DiscoveredCall(
                id = record.definition.id,
                h3CellId = record.definition.h3CellId,
                discoveredAt = FIXED_INSTANT,
                updatedAt = FIXED_INSTANT,
            ),
        )
    }

    @Test
    fun `invoke should return not in range when actor is outside interaction radius`() = runTest {
        // Given
        val clock = fixedClock()
        val actorLocation = GeoPoint(lat = 50.4700, lon = 30.5400)
        val record = record(
            id = "breach-node:v1:h3r9:cell-2",
            cellId = "cell-2",
            location = GeoPoint(lat = 50.4500, lon = 30.5200),
        )
        val repository = FakeBreachNodeRepository(records = listOf(record))
        val subject = DiscoverBreachNodeUseCase(repository, clock)

        // When
        val actual = subject(id = record.definition.id, actorLocation = actorLocation)

        // Then
        actual shouldBe Output.Failure(
            BreachNodeError.NotInRange(
                id = record.definition.id,
                distanceMeters = actorLocation.distanceTo(record.definition.location),
                interactionRadiusMeters = record.definition.interactionRadiusMeters,
            ),
        )
    }

    @Test
    fun `invoke should mark breach controlled when actor is within interaction radius`() = runTest {
        // Given
        val clock = fixedClock()
        val actorLocation = GeoPoint(lat = 50.4500, lon = 30.5200)
        val record = record(
            id = "breach-node:v1:h3r9:cell-3",
            cellId = "cell-3",
            location = GeoPoint(lat = 50.4501, lon = 30.5201),
            state = BreachNodeState(
                breachNodeId = "breach-node:v1:h3r9:cell-3",
                h3CellId = "cell-3",
                discoveredAt = FIXED_INSTANT.minusSeconds(60),
                controlledAt = null,
                lockdownUntil = null,
                updatedAt = FIXED_INSTANT.minusSeconds(60),
            ),
        )
        val repository = FakeBreachNodeRepository(records = listOf(record))
        val subject = CompleteBreachUseCase(repository, clock)

        // When
        val actual = subject(id = record.definition.id, actorLocation = actorLocation)

        // Then
        actual shouldBe Output.Success(
            BreachNodeState(
                breachNodeId = record.definition.id,
                h3CellId = record.definition.h3CellId,
                discoveredAt = FIXED_INSTANT.minusSeconds(60),
                controlledAt = FIXED_INSTANT,
                lockdownUntil = null,
                updatedAt = FIXED_INSTANT,
            ),
        )
        repository.controlledCalls shouldContainExactly listOf(
            FakeBreachNodeRepository.ControlledCall(
                id = record.definition.id,
                h3CellId = record.definition.h3CellId,
                controlledAt = FIXED_INSTANT,
                updatedAt = FIXED_INSTANT,
            ),
        )
    }

    @Test
    fun `invoke should emit controlled cells when requested bounds include controlled breaches`() = runTest {
        // Given
        val bounds = GeoBounds(south = 50.44, west = 30.51, north = 50.46, east = 30.53)
        val repository = FakeBreachNodeRepository(controlledCells = setOf("cell-controlled"))
        val subject = ObserveControlledBreachRevealCellsUseCase(repository)

        // When
        val actual = subject(bounds).first()

        // Then
        actual shouldBe Output.Success(setOf("cell-controlled"))
        repository.controlledBounds shouldContainExactly listOf(bounds)
    }

    @Test
    fun `invoke should emit discovered and controlled breach nodes when requested bounds include visible records`() =
        runTest {
            // Given
            val bounds = GeoBounds(south = 50.44, west = 30.51, north = 50.46, east = 30.53)
            val repository = FakeBreachNodeRepository(
                records = listOf(
                    record(
                        id = "breach-node:v1:h3r9:cell-discovered",
                        cellId = "cell-discovered",
                        location = GeoPoint(lat = 50.4501, lon = 30.5201),
                        state = BreachNodeState(
                            breachNodeId = "breach-node:v1:h3r9:cell-discovered",
                            h3CellId = "cell-discovered",
                            discoveredAt = FIXED_INSTANT.minusSeconds(60),
                            controlledAt = null,
                            lockdownUntil = null,
                            updatedAt = FIXED_INSTANT.minusSeconds(60),
                        ),
                    ),
                    record(
                        id = "breach-node:v1:h3r9:cell-controlled",
                        cellId = "cell-controlled",
                        location = GeoPoint(lat = 50.4502, lon = 30.5202),
                        state = BreachNodeState(
                            breachNodeId = "breach-node:v1:h3r9:cell-controlled",
                            h3CellId = "cell-controlled",
                            discoveredAt = FIXED_INSTANT.minusSeconds(120),
                            controlledAt = FIXED_INSTANT.minusSeconds(30),
                            lockdownUntil = null,
                            updatedAt = FIXED_INSTANT.minusSeconds(30),
                        ),
                    ),
                    record(
                        id = "breach-node:v1:h3r9:cell-hidden",
                        cellId = "cell-hidden",
                        location = GeoPoint(lat = 50.4503, lon = 30.5203),
                    ),
                ),
            )
            val subject = ObserveVisibleBreachNodesUseCase(repository)

            // When
            val actual = subject(bounds).first()

            // Then
            val visible = (actual as Output.Success).value
            visible.map { it.definition.id } shouldContainExactly listOf(
                "breach-node:v1:h3r9:cell-discovered",
                "breach-node:v1:h3r9:cell-controlled",
            )
            visible.map { it.phase } shouldContainExactly listOf(
                BreachNodePhase.DISCOVERED,
                BreachNodePhase.CONTROLLED,
            )
            visible.map { it.state?.breachNodeId } shouldContainExactly listOf(
                "breach-node:v1:h3r9:cell-discovered",
                "breach-node:v1:h3r9:cell-controlled",
            )
            repository.visibleBounds shouldContainExactly listOf(bounds)
        }

    private fun fixedClock(): Clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)

    private fun record(
        id: String,
        cellId: String,
        location: GeoPoint,
        state: BreachNodeState? = null,
    ): BreachNodeRecord =
        BreachNodeRecord(
            definition = BreachNodeDefinition(
                id = id,
                h3CellId = cellId,
                districtName = "district-$cellId",
                description = null,
                location = location,
                interactionRadiusMeters = 35.0,
                controlledH3CellIds = setOf(cellId),
            ),
            state = state,
        )

    private class FakeBreachNodeRepository(
        private val records: List<BreachNodeRecord> = emptyList(),
        private val controlledCells: Set<String> = emptySet(),
    ) : BreachNodeRepository {

        val discoveredCalls = mutableListOf<DiscoveredCall>()
        val controlledCalls = mutableListOf<ControlledCall>()
        val visibleBounds = mutableListOf<GeoBounds>()
        val controlledBounds = mutableListOf<GeoBounds>()

        override fun observeInBounds(bounds: GeoBounds): Flow<List<BreachNodeRecord>> {
            visibleBounds += bounds
            return flowOf(records)
        }

        override suspend fun getInBounds(bounds: GeoBounds): List<BreachNodeRecord> = records

        override suspend fun getForCells(h3CellIds: Collection<String>): List<BreachNodeRecord> =
            records.filter { record -> record.definition.h3CellId in h3CellIds }

        override suspend fun getById(id: String): BreachNodeRecord? =
            records.firstOrNull { record -> record.definition.id == id }

        override suspend fun getByH3CellId(h3CellId: String): BreachNodeRecord? =
            records.firstOrNull { record -> record.definition.h3CellId == h3CellId }

        override suspend fun upsertDiscovered(
            id: String,
            h3CellId: String,
            discoveredAt: Instant,
            updatedAt: Instant,
        ): Boolean {
            discoveredCalls += DiscoveredCall(
                id = id,
                h3CellId = h3CellId,
                discoveredAt = discoveredAt,
                updatedAt = updatedAt,
            )
            return true
        }

        override suspend fun markControlled(
            id: String,
            h3CellId: String,
            controlledAt: Instant,
            updatedAt: Instant,
        ): Boolean {
            controlledCalls += ControlledCall(
                id = id,
                h3CellId = h3CellId,
                controlledAt = controlledAt,
                updatedAt = updatedAt,
            )
            return true
        }

        override fun observeControlledCells(bounds: GeoBounds): Flow<Set<String>> {
            controlledBounds += bounds
            return flowOf(controlledCells)
        }

        data class DiscoveredCall(
            val id: String,
            val h3CellId: String,
            val discoveredAt: Instant,
            val updatedAt: Instant,
        )

        data class ControlledCall(
            val id: String,
            val h3CellId: String,
            val controlledAt: Instant,
            val updatedAt: Instant,
        )
    }

    private companion object {
        val FIXED_INSTANT: Instant = Instant.parse("2026-05-15T12:00:00Z")
    }
}
