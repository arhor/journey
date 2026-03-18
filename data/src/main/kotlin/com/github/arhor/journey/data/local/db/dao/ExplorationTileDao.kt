package com.github.arhor.journey.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.github.arhor.journey.data.local.db.entity.ExplorationTileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExplorationTileDao {

    @Query(
        """
        SELECT *
        FROM exploration_tile
        WHERE zoom = :zoom
            AND x BETWEEN :minX AND :maxX
            AND y BETWEEN :minY AND :maxY
        ORDER BY y ASC, x ASC
        """,
    )
    fun observeByRange(
        zoom: Int,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
    ): Flow<List<ExplorationTileEntity>>

    @Query(
        """
        INSERT INTO exploration_tile (zoom, x, y, light)
        VALUES (:zoom, :x, :y, :light)
        ON CONFLICT(zoom, x, y) DO UPDATE
        SET light = MAX(exploration_tile.light, excluded.light)
        """,
    )
    suspend fun insertOrAccumulate(
        zoom: Int,
        x: Int,
        y: Int,
        light: Float,
    )

    @Transaction
    suspend fun accumulate(entities: List<ExplorationTileEntity>) {
        entities.forEach { entity ->
            insertOrAccumulate(
                zoom = entity.zoom,
                x = entity.x,
                y = entity.y,
                light = entity.light,
            )
        }
    }

    @Query("DELETE FROM exploration_tile")
    suspend fun clear()
}
