package com.github.arhor.journey.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.arhor.journey.data.local.db.entity.WatchtowerStateEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface WatchtowerStateDao {

    @Query(
        """
        SELECT *
        FROM watchtower_state
        WHERE watchtowerId IN (:ids)
        ORDER BY watchtowerId ASC
        """,
    )
    fun observeByIds(ids: List<String>): Flow<List<WatchtowerStateEntity>>

    @Query(
        """
        SELECT *
        FROM watchtower_state
        WHERE watchtowerId IN (:ids)
        ORDER BY watchtowerId ASC
        """,
    )
    suspend fun getByIds(ids: List<String>): List<WatchtowerStateEntity>

    @Query(
        """
        SELECT *
        FROM watchtower_state
        WHERE watchtowerId = :id
        LIMIT 1
        """,
    )
    suspend fun getById(id: String): WatchtowerStateEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertState(entity: WatchtowerStateEntity): Long

    @Query(
        """
        UPDATE watchtower_state
        SET claimedAt = :claimedAt,
            level = :level,
            updatedAt = :updatedAt
        WHERE watchtowerId = :watchtowerId
          AND claimedAt IS NULL
        """,
    )
    suspend fun markClaimed(
        watchtowerId: String,
        claimedAt: Instant,
        level: Int,
        updatedAt: Instant,
    ): Int

    @Query(
        """
        UPDATE watchtower_state
        SET level = :level,
            updatedAt = :updatedAt
        WHERE watchtowerId = :watchtowerId
          AND claimedAt IS NOT NULL
          AND level < :level
        """,
    )
    suspend fun setLevel(
        watchtowerId: String,
        level: Int,
        updatedAt: Instant,
    ): Int
}
