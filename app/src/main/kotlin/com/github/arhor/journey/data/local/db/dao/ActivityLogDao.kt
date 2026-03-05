package com.github.arhor.journey.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.github.arhor.journey.data.local.db.entity.ActivityLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityLogDao {

    @Query("SELECT * FROM activity_log ORDER BY started_at_ms DESC")
    fun observeRecent(): Flow<List<ActivityLogEntity>>

    @Query(
        """
        SELECT * FROM activity_log
        WHERE external_record_id = :externalRecordId
          AND origin_package_name = :originPackageName
          AND time_bounds_hash = :timeBoundsHash
        LIMIT 1
        """,
    )
    suspend fun findByImportIdentity(
        externalRecordId: String,
        originPackageName: String,
        timeBoundsHash: String,
    ): ActivityLogEntity?

    @Query(
        """
        SELECT * FROM activity_log
        WHERE source = :source
          AND started_at_ms < :endedAtMs
          AND (started_at_ms + (duration_seconds * 1000)) > :startedAtMs
        """,
    )
    suspend fun findOverlappingBySource(
        source: String,
        startedAtMs: Long,
        endedAtMs: Long,
    ): List<ActivityLogEntity>

    @Query("DELETE FROM activity_log WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insert(entity: ActivityLogEntity): Long
}
