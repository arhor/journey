package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.BreachNodeStateDao
import com.github.arhor.journey.data.local.db.entity.BreachNodeStateEntity
import com.github.arhor.journey.data.mapper.toDomain
import com.github.arhor.journey.data.mapper.toRecord
import com.github.arhor.journey.domain.internal.BreachBalance
import com.github.arhor.journey.domain.internal.BreachNodeGeneration
import com.github.arhor.journey.domain.model.BreachNodeDefinition
import com.github.arhor.journey.domain.model.BreachNodeRecord
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.repository.BreachNodeRepository
import com.github.arhor.journey.domain.spatial.H3Grid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeterministicBreachNodeRepository @Inject constructor(
    private val dao: BreachNodeStateDao,
    private val h3Grid: H3Grid,
) : BreachNodeRepository {

    override fun observeInBounds(bounds: GeoBounds): Flow<List<BreachNodeRecord>> {
        val definitions = definitionsInBounds(bounds)
        if (definitions.isEmpty()) {
            return flowOf(emptyList())
        }

        return dao.observeByCellIds(definitions.map(BreachNodeDefinition::h3CellId))
            .map { states -> composeRecords(definitions, states) }
    }

    override suspend fun getInBounds(bounds: GeoBounds): List<BreachNodeRecord> =
        getForCells(definitionsInBounds(bounds).map(BreachNodeDefinition::h3CellId))

    override suspend fun getForCells(h3CellIds: Collection<String>): List<BreachNodeRecord> {
        val definitions = definitionsForCells(h3CellIds)
        if (definitions.isEmpty()) {
            return emptyList()
        }

        val states = dao.getByCellIds(definitions.map(BreachNodeDefinition::h3CellId))
        return composeRecords(definitions, states)
    }

    override suspend fun getById(id: String): BreachNodeRecord? {
        val h3CellId = breachNodeCellIdOrNull(id) ?: return null
        val definition = BreachNodeGeneration.definitionForCell(h3CellId, h3Grid) ?: return null

        return definition.toRecord(
            state = dao.getByH3CellId(h3CellId)?.toDomain(),
        )
    }

    override suspend fun getByH3CellId(h3CellId: String): BreachNodeRecord? {
        val definition = BreachNodeGeneration.definitionForCell(h3CellId, h3Grid) ?: return null

        return definition.toRecord(
            state = dao.getByH3CellId(h3CellId)?.toDomain(),
        )
    }

    override suspend fun upsertDiscovered(
        id: String,
        h3CellId: String,
        discoveredAt: Instant,
        updatedAt: Instant,
    ): Boolean {
        val definition = BreachNodeGeneration.definitionForCell(h3CellId, h3Grid) ?: return false
        val existing = dao.getByH3CellId(h3CellId)
        dao.upsert(
            entity = BreachNodeStateEntity(
                breachNodeId = existing?.breachNodeId ?: definition.id,
                h3CellId = h3CellId,
                discoveredAt = discoveredAt,
                controlledAt = existing?.controlledAt,
                lockdownUntil = existing?.lockdownUntil,
                updatedAt = updatedAt,
            ),
        )
        return true
    }

    override suspend fun markControlled(
        id: String,
        h3CellId: String,
        controlledAt: Instant,
        updatedAt: Instant,
    ): Boolean {
        val definition = BreachNodeGeneration.definitionForCell(h3CellId, h3Grid) ?: return false
        val existing = dao.getByH3CellId(h3CellId)
        val updatedRows = dao.markControlled(
            id = existing?.breachNodeId ?: definition.id,
            h3CellId = h3CellId,
            controlledAt = controlledAt,
            updatedAt = updatedAt,
        )
        if (updatedRows > 0) {
            return true
        }

        dao.upsert(
            entity = BreachNodeStateEntity(
                breachNodeId = existing?.breachNodeId ?: definition.id,
                h3CellId = h3CellId,
                discoveredAt = existing?.discoveredAt,
                controlledAt = controlledAt,
                lockdownUntil = existing?.lockdownUntil,
                updatedAt = updatedAt,
            ),
        )
        return true
    }

    override fun observeControlledCells(bounds: GeoBounds): Flow<Set<String>> {
        val cellIds = cellIdsInBounds(bounds)
        if (cellIds.isEmpty()) {
            return flowOf(emptySet())
        }

        return dao.observeControlledCellIdsByCellIds(cellIds)
            .map { it.toSet() }
    }

    private fun definitionsInBounds(bounds: GeoBounds): List<BreachNodeDefinition> =
        definitionsForCells(cellIdsInBounds(bounds))

    private fun definitionsForCells(cellIds: Collection<String>): List<BreachNodeDefinition> =
        BreachNodeGeneration.definitionsForCells(
            cellIds = cellIds,
            h3Grid = h3Grid,
        )

    private fun cellIdsInBounds(bounds: GeoBounds): List<String> =
        h3Grid.cellsInBounds(bounds, BreachBalance.H3_RESOLUTION)

    private fun composeRecords(
        definitions: List<BreachNodeDefinition>,
        states: List<BreachNodeStateEntity>,
    ): List<BreachNodeRecord> {
        val statesByCellId = states.associateBy(BreachNodeStateEntity::h3CellId)
        return definitions.map { definition ->
            definition.toRecord(
                state = statesByCellId[definition.h3CellId]?.toDomain(),
            )
        }
    }

    private fun breachNodeCellIdOrNull(id: String): String? {
        val prefix = "breach-node:v${BreachBalance.GENERATOR_VERSION}:h3r${BreachBalance.H3_RESOLUTION}:"
        return id.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
    }
}
