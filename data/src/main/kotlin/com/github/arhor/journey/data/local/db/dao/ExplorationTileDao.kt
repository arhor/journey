package com.github.arhor.journey.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.arhor.journey.data.local.db.entity.ExploredTileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExplorationTileDao {

    @Query(
        """
        SELECT *
        FROM explored_tiles
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
    ): Flow<List<ExploredTileEntity>>

    @Query(
        """
        SELECT *
        FROM explored_tiles
        WHERE zoom = :zoom
            AND x BETWEEN :minX AND :maxX
            AND y BETWEEN :minY AND :maxY
        ORDER BY y ASC, x ASC
        """,
    )
    suspend fun getByRange(
        zoom: Int,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
    ): List<ExploredTileEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entities: List<ExploredTileEntity>): List<Long>

    @Query("DELETE FROM explored_tiles")
    suspend fun clear()
}
