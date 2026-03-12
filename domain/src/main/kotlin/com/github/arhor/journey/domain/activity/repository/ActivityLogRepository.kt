package com.github.arhor.journey.domain.activity.repository

import com.github.arhor.journey.domain.activity.model.ActivityLogEntry
import com.github.arhor.journey.domain.activity.model.ActivityLogInsertResult
import com.github.arhor.journey.domain.activity.model.RecordedActivity
import com.github.arhor.journey.domain.player.model.Reward
import kotlinx.coroutines.flow.Flow

interface ActivityLogRepository {

    /**
     * Emits activity history in reverse chronological order (newest first).
     */
    fun observeHistory(): Flow<List<ActivityLogEntry>>

    /**
     * Inserts a new activity log entry.
     */
    suspend fun insert(
        recorded: RecordedActivity,
        reward: Reward,
    ): ActivityLogInsertResult
}
