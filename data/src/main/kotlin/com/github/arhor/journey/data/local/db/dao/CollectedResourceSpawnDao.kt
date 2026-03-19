package com.github.arhor.journey.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.arhor.journey.data.local.db.entity.CollectedResourceSpawnEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectedResourceSpawnDao {

    @Query(
        """
        SELECT *
        FROM collected_resource_spawns
        WHERE hero_id = :heroId
        ORDER BY collected_at DESC
        """,
    )
    fun observeAll(heroId: String): Flow<List<CollectedResourceSpawnEntity>>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM collected_resource_spawns
            WHERE hero_id = :heroId
                AND spawn_id = :spawnId
        )
        """,
    )
    suspend fun exists(
        heroId: String,
        spawnId: String,
    ): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: CollectedResourceSpawnEntity): Long
}
