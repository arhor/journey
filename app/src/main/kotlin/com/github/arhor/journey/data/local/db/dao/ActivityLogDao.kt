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

    @Insert
    suspend fun insert(entity: ActivityLogEntity): Long
}

