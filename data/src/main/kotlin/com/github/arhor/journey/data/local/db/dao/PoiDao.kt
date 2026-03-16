package com.github.arhor.journey.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.github.arhor.journey.data.local.db.entity.PoiEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PoiDao {

    @Query("SELECT * FROM poi ORDER BY name ASC")
    fun observeAll(): Flow<List<PoiEntity>>

    @Query("SELECT * FROM poi WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PoiEntity?

    @Query("SELECT COUNT(*) FROM poi")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(entity: PoiEntity)

    @Upsert
    suspend fun upsertAll(entities: List<PoiEntity>)
}

