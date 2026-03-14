package com.github.arhor.journey.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.github.arhor.journey.data.local.db.entity.HeroEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HeroDao {

    @Query("SELECT * FROM hero WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<HeroEntity?>

    @Query("SELECT * FROM hero WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HeroEntity?

    @Upsert
    suspend fun upsert(entity: HeroEntity)
}

