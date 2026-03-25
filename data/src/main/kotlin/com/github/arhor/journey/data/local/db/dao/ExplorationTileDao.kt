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

    @Query(
        """
        SELECT ((zoom << 48) | ((x & 16777215) << 24) | (y & 16777215))
        FROM explored_tiles
        WHERE zoom = :zoom
            AND x = :x
            AND y = :y
        LIMIT 1
        """,
    )
    suspend fun getPackedByCoordinates(
        zoom: Int,
        x: Int,
        y: Int,
    ): Long?

    @Query(
        """
        SELECT ((zoom << 48) | ((x & 16777215) << 24) | (y & 16777215))
        FROM explored_tiles
        WHERE zoom = :zoom
            AND x BETWEEN :minX AND :maxX
            AND y BETWEEN :minY AND :maxY
        ORDER BY zoom ASC, y ASC, x ASC
        """,
    )
    suspend fun getPackedByRange(
        zoom: Int,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
    ): List<Long>

    @Query(
        """
        SELECT ((zoom << 48) | ((x & 16777215) << 24) | (y & 16777215))
        FROM explored_tiles
        WHERE zoom = :zoom
            AND x BETWEEN :minX AND :maxX
            AND y BETWEEN :minY AND :maxY
        ORDER BY zoom ASC, y ASC, x ASC
        """,
    )
    fun observePackedByRange(
        zoom: Int,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
    ): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entities: List<ExploredTileEntity>): List<Long>

    @Query("DELETE FROM explored_tiles")
    suspend fun clear()
}
