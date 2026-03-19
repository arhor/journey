package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.model.HeroResource
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface HeroInventoryRepository {

    fun observeAll(heroId: String): Flow<List<HeroResource>>

    fun observeAmount(
        heroId: String,
        resourceTypeId: String,
    ): Flow<Int>

    suspend fun getAmount(
        heroId: String,
        resourceTypeId: String,
    ): Int

    suspend fun setAmount(
        heroId: String,
        resourceTypeId: String,
        amount: Int,
        updatedAt: Instant,
    ): HeroResource

    suspend fun addAmount(
        heroId: String,
        resourceTypeId: String,
        amount: Int,
        updatedAt: Instant,
    ): HeroResource

    suspend fun spendAmount(
        heroId: String,
        resourceTypeId: String,
        amount: Int,
        updatedAt: Instant,
    ): HeroResource?
}
