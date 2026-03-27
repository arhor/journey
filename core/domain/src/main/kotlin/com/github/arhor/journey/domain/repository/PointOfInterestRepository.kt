package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.model.PointOfInterest
import kotlinx.coroutines.flow.Flow

interface PointOfInterestRepository {

    fun observeAll(): Flow<List<PointOfInterest>>

    suspend fun getById(id: Long): PointOfInterest?

    suspend fun upsert(pointOfInterest: PointOfInterest): Long
}
