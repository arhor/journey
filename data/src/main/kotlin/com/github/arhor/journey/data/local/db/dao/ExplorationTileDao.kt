package com.github.arhor.journey.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entities: List<ExplorationTileEntity>): List<Long>

    @Query("DELETE FROM exploration_tile")
    suspend fun clear()
}
