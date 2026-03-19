package com.github.arhor.journey.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.arhor.journey.data.local.db.entity.DiscoveredPoiEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiscoveredPoiDao {

    @Query("SELECT * FROM discovered_poi ORDER BY discoveredAt DESC")
    fun observeAll(): Flow<List<DiscoveredPoiEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: DiscoveredPoiEntity): Long
}
