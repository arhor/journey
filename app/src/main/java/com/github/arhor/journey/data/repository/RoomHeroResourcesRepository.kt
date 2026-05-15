package com.github.arhor.journey.data.repository

import com.github.arhor.journey.domain.TransactionRunner
import com.github.arhor.journey.domain.model.HeroResource
import com.github.arhor.journey.domain.repository.HeroInventoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomHeroResourcesRepository @Inject constructor(
    private val transactionRunner: TransactionRunner,
) : HeroInventoryRepository {
    private val mutex = Mutex()
    private val state = MutableStateFlow<Map<ResourceKey, HeroResource>>(emptyMap())

    override fun observeAll(heroId: String): Flow<List<HeroResource>> =
        state.map { values ->
            values.values
                .filter { resource -> resource.heroId == heroId }
                .sortedBy(HeroResource::resourceTypeId)
        }

    override fun observeAmount(
        heroId: String,
        resourceTypeId: String,
    ): Flow<Int> =
        state.map { values ->
            values[ResourceKey(heroId = heroId, resourceTypeId = resourceTypeId)]?.amount ?: 0
        }

    override suspend fun getAmount(
        heroId: String,
        resourceTypeId: String,
    ): Int =
        state.value[ResourceKey(heroId = heroId, resourceTypeId = resourceTypeId)]?.amount ?: 0

    override suspend fun setAmount(
        heroId: String,
        resourceTypeId: String,
        amount: Int,
        updatedAt: Instant,
    ): HeroResource {
        require(amount >= 0) { "Resource amount must not be negative." }

        return transactionRunner.runInTransaction {
            val updated = HeroResource(
                heroId = heroId,
                resourceTypeId = resourceTypeId,
                amount = amount,
                updatedAt = updatedAt,
            )
            upsertResource(updated)
            updated
        }
    }

    override suspend fun addAmount(
        heroId: String,
        resourceTypeId: String,
        amount: Int,
        updatedAt: Instant,
    ): HeroResource {
        require(amount > 0) { "Added resource amount must be greater than zero." }

        return transactionRunner.runInTransaction {
            val key = ResourceKey(heroId = heroId, resourceTypeId = resourceTypeId)
            val previous = state.value[key]?.amount ?: 0
            val updated = HeroResource(
                heroId = heroId,
                resourceTypeId = resourceTypeId,
                amount = previous + amount,
                updatedAt = updatedAt,
            )
            upsertResource(updated)
            updated
        }
    }

    override suspend fun spendAmount(
        heroId: String,
        resourceTypeId: String,
        amount: Int,
        updatedAt: Instant,
    ): HeroResource? {
        require(amount > 0) { "Spent resource amount must be greater than zero." }

        return transactionRunner.runInTransaction {
            val key = ResourceKey(heroId = heroId, resourceTypeId = resourceTypeId)
            val previous = state.value[key] ?: return@runInTransaction null
            if (previous.amount < amount) {
                return@runInTransaction null
            }
            val updated = previous.copy(
                amount = previous.amount - amount,
                updatedAt = updatedAt,
            )
            upsertResource(updated)
            updated
        }
    }

    private suspend fun upsertResource(resource: HeroResource) {
        mutex.withLock {
            val updated = state.value.toMutableMap()
            updated[ResourceKey(heroId = resource.heroId, resourceTypeId = resource.resourceTypeId)] = resource
            state.value = updated.toMap()
        }
    }

    private data class ResourceKey(
        val heroId: String,
        val resourceTypeId: String,
    )
}
