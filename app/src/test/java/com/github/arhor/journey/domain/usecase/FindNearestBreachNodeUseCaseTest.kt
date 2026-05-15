package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.testing.FakeH3Grid
import com.github.arhor.journey.domain.model.BreachNodeDefinition
import com.github.arhor.journey.domain.model.BreachNodeRecord
import com.github.arhor.journey.domain.model.BreachNodeState
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.error.BreachNodeError
import com.github.arhor.journey.domain.repository.BreachNodeRepository
import com.github.arhor.journey.domain.spatial.H3Grid
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class FindNearestBreachNodeUseCaseTest {

    @Test
    fun `invoke should return nearest uncontrolled breach node when candidates exist in the scan disk`() = runTest {
        // Given
        val actor = GeoPoint(lat = 50.45, lon = 30.52)
        val scanCells = listOf("origin", "cell-controlled", "cell-near", "cell-far")
        val h3Grid = RecordingH3Grid(
            delegate = FakeH3Grid(
                originCell = "origin",
                disk = scanCells,
                centers = mapOf(
                    "origin" to actor,
                    "cell-controlled" to GeoPoint(lat = 50.4501, lon = 30.5201),
                    "cell-near" to GeoPoint(lat = 50.4502, lon = 30.5202),
                    "cell-far" to GeoPoint(lat = 50.46, lon = 30.53),
                ),
                averageEdgeLengthMeters = 180.0,
            ),
        )
        val repository = FakeBreachNodeRepository(
            records = listOf(
                controlledRecord("cell-controlled", GeoPoint(lat = 50.4501, lon = 30.5201)),
                uncontrolledRecord("cell-near", GeoPoint(lat = 50.4502, lon = 30.5202)),
                uncontrolledRecord("cell-far", GeoPoint(lat = 50.46, lon = 30.53)),
            ),
        )
        val subject = FindNearestBreachNodeUseCase(repository, h3Grid)

        // When
        val actual = subject(actor)

        // Then
        actual shouldBe Output.Success(
            uncontrolledRecord("cell-near", GeoPoint(lat = 50.4502, lon = 30.5202)),
        )
        repository.requestedCellIds shouldContainExactly scanCells
        h3Grid.gridDiskCalls shouldContainExactly listOf(RecordingH3Grid.GridDiskCall("origin", 6))
    }

    @Test
    fun `invoke should return not found when scan disk contains no breach candidates`() = runTest {
        // Given
        val actor = GeoPoint(lat = 50.45, lon = 30.52)
        val h3Grid = FakeH3Grid(
            originCell = "origin",
            disk = listOf("origin"),
            centers = mapOf("origin" to actor),
            averageEdgeLengthMeters = 180.0,
        )
        val repository = FakeBreachNodeRepository(records = emptyList())
        val subject = FindNearestBreachNodeUseCase(repository, h3Grid)

        // When
        val actual = subject(actor)

        // Then
        actual shouldBe Output.Failure(BreachNodeError.NotFound)
    }

    @Test
    fun `invoke should filter out controlled breaches before choosing the nearest candidate`() = runTest {
        // Given
        val actor = GeoPoint(lat = 50.45, lon = 30.52)
        val h3Grid = FakeH3Grid(
            originCell = "origin",
            disk = listOf("origin", "cell-controlled", "cell-open"),
            centers = mapOf(
                "origin" to actor,
                "cell-controlled" to GeoPoint(lat = 50.4501, lon = 30.5201),
                "cell-open" to GeoPoint(lat = 50.455, lon = 30.525),
            ),
        )
        val repository = FakeBreachNodeRepository(
            records = listOf(
                controlledRecord("cell-controlled", GeoPoint(lat = 50.4501, lon = 30.5201)),
                uncontrolledRecord("cell-open", GeoPoint(lat = 50.455, lon = 30.525)),
            ),
        )
        val subject = FindNearestBreachNodeUseCase(repository, h3Grid)

        // When
        val actual = subject(actor)

        // Then
        actual shouldBe Output.Success(
            uncontrolledRecord("cell-open", GeoPoint(lat = 50.455, lon = 30.525)),
        )
    }

    @Test
    fun `invoke should filter out lockdown breaches before choosing the nearest candidate`() = runTest {
        // Given
        val actor = GeoPoint(lat = 50.45, lon = 30.52)
        val h3Grid = FakeH3Grid(
            originCell = "origin",
            disk = listOf("origin", "cell-lockdown", "cell-open"),
            centers = mapOf(
                "origin" to actor,
                "cell-lockdown" to GeoPoint(lat = 50.4501, lon = 30.5201),
                "cell-open" to GeoPoint(lat = 50.455, lon = 30.525),
            ),
        )
        val repository = FakeBreachNodeRepository(
            records = listOf(
                lockdownRecord("cell-lockdown", GeoPoint(lat = 50.4501, lon = 30.5201)),
                uncontrolledRecord("cell-open", GeoPoint(lat = 50.455, lon = 30.525)),
            ),
        )
        val subject = FindNearestBreachNodeUseCase(repository, h3Grid)

        // When
        val actual = subject(actor)

        // Then
        actual shouldBe Output.Success(
            uncontrolledRecord("cell-open", GeoPoint(lat = 50.455, lon = 30.525)),
        )
    }

    private class FakeBreachNodeRepository(
        private val records: List<BreachNodeRecord>,
    ) : BreachNodeRepository {

        val requestedCellIds = mutableListOf<String>()

        override fun observeInBounds(bounds: com.github.arhor.journey.domain.model.GeoBounds): Flow<List<BreachNodeRecord>> =
            emptyFlow()

        override suspend fun getInBounds(bounds: com.github.arhor.journey.domain.model.GeoBounds): List<BreachNodeRecord> =
            emptyList()

        override suspend fun getForCells(h3CellIds: Collection<String>): List<BreachNodeRecord> {
            requestedCellIds += h3CellIds
            return records.filter { record -> record.definition.h3CellId in h3CellIds }
        }

        override suspend fun getById(id: String): BreachNodeRecord? = null

        override suspend fun getByH3CellId(h3CellId: String): BreachNodeRecord? = null

        override suspend fun upsertDiscovered(
            id: String,
            h3CellId: String,
            discoveredAt: Instant,
            updatedAt: Instant,
        ): Boolean = false

        override suspend fun markControlled(
            id: String,
            h3CellId: String,
            controlledAt: Instant,
            updatedAt: Instant,
        ): Boolean = false

        override fun observeControlledCells(bounds: com.github.arhor.journey.domain.model.GeoBounds): Flow<Set<String>> =
            emptyFlow()
    }

    private class RecordingH3Grid(
        private val delegate: FakeH3Grid,
    ) : H3Grid {

        data class GridDiskCall(
            val cellId: String,
            val radius: Int,
        )

        val gridDiskCalls = mutableListOf<GridDiskCall>()

        override fun cellId(lat: Double, lon: Double, resolution: Int): String =
            delegate.cellId(lat, lon, resolution)

        override fun cellResolution(cellId: String): Int =
            delegate.cellResolution(cellId)

        override fun cellCenter(cellId: String): GeoPoint =
            delegate.cellCenter(cellId)

        override fun cellBoundary(cellId: String): List<GeoPoint> =
            delegate.cellBoundary(cellId)

        override fun gridDisk(cellId: String, radius: Int): List<String> {
            gridDiskCalls += GridDiskCall(cellId = cellId, radius = radius)
            return delegate.gridDisk(cellId, radius)
        }

        override fun gridDistance(originCellId: String, destinationCellId: String): Long =
            delegate.gridDistance(originCellId, destinationCellId)

        override fun averageEdgeLengthMeters(resolution: Int): Double =
            delegate.averageEdgeLengthMeters(resolution)

        override fun cellsInBounds(bounds: com.github.arhor.journey.domain.model.GeoBounds, resolution: Int): List<String> =
            delegate.cellsInBounds(bounds, resolution)
    }

    private fun uncontrolledRecord(
        cellId: String,
        location: GeoPoint,
    ): BreachNodeRecord = BreachNodeRecord(
        definition = definition(cellId, location),
        state = null,
    )

    private fun controlledRecord(
        cellId: String,
        location: GeoPoint,
    ): BreachNodeRecord = BreachNodeRecord(
        definition = definition(cellId, location),
        state = BreachNodeState(
            breachNodeId = "breach-node:$cellId",
            h3CellId = cellId,
            discoveredAt = FIXED_INSTANT,
            controlledAt = FIXED_INSTANT,
            lockdownUntil = null,
            updatedAt = FIXED_INSTANT,
        ),
    )

    private fun lockdownRecord(
        cellId: String,
        location: GeoPoint,
    ): BreachNodeRecord = BreachNodeRecord(
        definition = definition(cellId, location),
        state = BreachNodeState(
            breachNodeId = "breach-node:$cellId",
            h3CellId = cellId,
            discoveredAt = FIXED_INSTANT,
            controlledAt = null,
            lockdownUntil = FIXED_INSTANT,
            updatedAt = FIXED_INSTANT,
        ),
    )

    private fun definition(
        cellId: String,
        location: GeoPoint,
    ): BreachNodeDefinition = BreachNodeDefinition(
        id = "breach-node:$cellId",
        h3CellId = cellId,
        districtName = "district-$cellId",
        description = null,
        location = location,
        interactionRadiusMeters = 35.0,
        controlledH3CellIds = setOf(cellId),
    )

    private companion object {
        val FIXED_INSTANT: Instant = Instant.parse("2026-05-15T12:00:00Z")
    }
}
