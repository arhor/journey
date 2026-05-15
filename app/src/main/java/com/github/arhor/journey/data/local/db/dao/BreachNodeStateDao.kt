package com.github.arhor.journey.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.github.arhor.journey.data.local.db.entity.BreachNodeStateEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface BreachNodeStateDao {

    @Query(
        """
        SELECT *
          FROM breach_node_state
         WHERE h3CellId IN (:h3CellIds)
         ORDER BY breachNodeId ASC
        """,
    )
    fun observeByCellIds(h3CellIds: Collection<String>): Flow<List<BreachNodeStateEntity>>

    @Query(
        """
        SELECT *
          FROM breach_node_state
         WHERE h3CellId IN (:h3CellIds)
         ORDER BY breachNodeId ASC
        """,
    )
    suspend fun getByCellIds(h3CellIds: Collection<String>): List<BreachNodeStateEntity>

    @Query(
        """
        SELECT *
          FROM breach_node_state
         WHERE breachNodeId = :id
         LIMIT 1
        """,
    )
    suspend fun getById(id: String): BreachNodeStateEntity?

    @Query(
        """
        SELECT *
          FROM breach_node_state
         WHERE h3CellId = :h3CellId
         LIMIT 1
        """,
    )
    suspend fun getByH3CellId(h3CellId: String): BreachNodeStateEntity?

    @Upsert
    suspend fun upsert(entity: BreachNodeStateEntity)

    @Query(
        """
        UPDATE breach_node_state
           SET controlledAt = :controlledAt,
               updatedAt = :updatedAt
         WHERE breachNodeId = :id
           AND h3CellId = :h3CellId
        """,
    )
    suspend fun markControlled(
        id: String,
        h3CellId: String,
        controlledAt: Instant,
        updatedAt: Instant,
    ): Int

    @Query(
        """
        SELECT h3CellId
          FROM breach_node_state
         WHERE controlledAt IS NOT NULL
           AND h3CellId IN (:h3CellIds)
         ORDER BY h3CellId ASC
        """,
    )
    fun observeControlledCellIdsByCellIds(h3CellIds: Collection<String>): Flow<List<String>>
}
