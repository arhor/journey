package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.HeroResourceDao
import com.github.arhor.journey.data.local.db.entity.HeroResourceEntity
import com.github.arhor.journey.data.mapper.toDomain
import com.github.arhor.journey.domain.TransactionRunner
import com.github.arhor.journey.domain.model.HeroResource
import com.github.arhor.journey.domain.repository.HeroInventoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomHeroResourcesRepository @Inject constructor(
    private val dao: HeroResourceDao,
    private val transactionRunner: TransactionRunner,
) : HeroInventoryRepository {

    override fun observeAll(heroId: String): Flow<List<HeroResource>> =
        dao.observeAll(heroId)
            .map { entities -> entities.map { it.toDomain() } }

    override fun observeAmount(
        heroId: String,
        resourceTypeId: String,
    ): Flow<Int> =
        dao.observeAmount(
            heroId = heroId,
            resourceTypeId = resourceTypeId,
        ).map { amount -> amount ?: 0 }

    override suspend fun getAmount(
        heroId: String,
        resourceTypeId: String,
    ): Int =
        dao.getAmount(
            heroId = heroId,
            resourceTypeId = resourceTypeId,
        ) ?: 0

    override suspend fun setAmount(
        heroId: String,
        resourceTypeId: String,
        amount: Int,
        updatedAt: Instant,
    ): HeroResource {
        require(amount >= 0) { "Resource amount must not be negative." }

        return transactionRunner.runInTransaction {
            dao.upsert(
                HeroResourceEntity(
                    heroId = heroId,
                    resourceTypeId = resourceTypeId,
                    amount = amount,
                    updatedAt = updatedAt,
                ),
            )

            getRequiredResource(
                heroId = heroId,
                resourceTypeId = resourceTypeId,
            ).toDomain()
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
            dao.incrementAmount(
                heroId = heroId,
                resourceTypeId = resourceTypeId,
                amountDelta = amount,
                updatedAt = updatedAt,
            )

            getRequiredResource(
                heroId = heroId,
                resourceTypeId = resourceTypeId,
            ).toDomain()
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
            val updatedRows = dao.decrementAmountIfEnough(
                heroId = heroId,
                resourceTypeId = resourceTypeId,
                amountDelta = amount,
                updatedAt = updatedAt,
            )

            if (updatedRows == 0) {
                null
            } else {
                getRequiredResource(
                    heroId = heroId,
                    resourceTypeId = resourceTypeId,
                ).toDomain()
            }
        }
    }

    private suspend fun getRequiredResource(
        heroId: String,
        resourceTypeId: String,
    ): HeroResourceEntity =
        requireNotNull(
            dao.getById(
                heroId = heroId,
                resourceTypeId = resourceTypeId,
            ),
        ) {
            "Hero resource must exist after mutation for heroId=$heroId resourceTypeId=$resourceTypeId."
        }
}
