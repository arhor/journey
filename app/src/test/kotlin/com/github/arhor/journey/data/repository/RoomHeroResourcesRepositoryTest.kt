package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.HeroResourceDao
import com.github.arhor.journey.data.local.db.entity.HeroResourceEntity
import com.github.arhor.journey.domain.TransactionRunner
import com.github.arhor.journey.domain.model.HeroResource
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class RoomHeroResourcesRepositoryTest {

    @Test
    fun `observeAll should map positive hero resources from dao entities`() = runTest {
        // Given
        val updatedAt = Instant.parse("2026-03-10T08:00:00Z")
        val dao = FakeHeroResourceDao(
            initialEntities = listOf(
                HeroResourceEntity(
                    heroId = "player",
                    typeId = "components",
                    amount = 0,
                    updatedAt = updatedAt,
                ),
                HeroResourceEntity(
                    heroId = "player",
                    typeId = "scrap",
                    amount = 2,
                    updatedAt = updatedAt,
                ),
                HeroResourceEntity(
                    heroId = "other",
                    typeId = "scrap",
                    amount = 7,
                    updatedAt = updatedAt,
                ),
            ),
        )
        val subject = RoomHeroResourcesRepository(
            dao = dao,
            transactionRunner = ImmediateTransactionRunner,
        )

        // When
        val actual = subject.observeAll(heroId = "player").first()

        // Then
        actual shouldBe listOf(
            HeroResource(
                heroId = "player",
                resourceTypeId = "scrap",
                amount = 2,
                updatedAt = updatedAt,
            ),
        )
    }

    @Test
    fun `observeAmount should emit updated amount when repository adds more resources`() = runTest {
        // Given
        val resourceTypeId = "scrap"
        val initialUpdatedAt = Instant.parse("2026-03-10T08:00:00Z")
        val nextUpdatedAt = Instant.parse("2026-03-10T08:05:00Z")
        val dao = FakeHeroResourceDao(
            initialEntities = listOf(
                HeroResourceEntity(
                    heroId = "player",
                    typeId = resourceTypeId,
                    amount = 1,
                    updatedAt = initialUpdatedAt,
                ),
            ),
        )
        val subject = RoomHeroResourcesRepository(
            dao = dao,
            transactionRunner = ImmediateTransactionRunner,
        )

        // When
        val emissions = mutableListOf<Int>()
        val collectionJob = launch {
            subject.observeAmount(heroId = "player", resourceTypeId = resourceTypeId)
                .take(2)
                .toList(emissions)
        }
        runCurrent()

        subject.addAmount(
            heroId = "player",
            resourceTypeId = resourceTypeId,
            amount = 2,
            updatedAt = nextUpdatedAt,
        )
        collectionJob.join()

        // Then
        emissions shouldBe listOf(1, 3)
    }

    @Test
    fun `spendAmount should decrement amount when enough resources are available`() = runTest {
        // Given
        val resourceTypeId = "ore"
        val initialUpdatedAt = Instant.parse("2026-03-10T09:00:00Z")
        val spendUpdatedAt = Instant.parse("2026-03-10T09:15:00Z")
        val dao = FakeHeroResourceDao(
            initialEntities = listOf(
                HeroResourceEntity(
                    heroId = "player",
                    typeId = resourceTypeId,
                    amount = 4,
                    updatedAt = initialUpdatedAt,
                ),
            ),
        )
        val subject = RoomHeroResourcesRepository(
            dao = dao,
            transactionRunner = ImmediateTransactionRunner,
        )

        // When
        val actual = subject.spendAmount(
            heroId = "player",
            resourceTypeId = resourceTypeId,
            amount = 3,
            updatedAt = spendUpdatedAt,
        )

        // Then
        actual shouldBe HeroResource(
            heroId = "player",
            resourceTypeId = resourceTypeId,
            amount = 1,
            updatedAt = spendUpdatedAt,
        )
        subject.getAmount(heroId = "player", resourceTypeId = resourceTypeId) shouldBe 1
    }

    @Test
    fun `spendAmount should return null when requested amount exceeds current balance`() = runTest {
        // Given
        val resourceTypeId = "ore"
        val updatedAt = Instant.parse("2026-03-10T09:15:00Z")
        val dao = FakeHeroResourceDao(
            initialEntities = listOf(
                HeroResourceEntity(
                    heroId = "player",
                    typeId = resourceTypeId,
                    amount = 2,
                    updatedAt = Instant.parse("2026-03-10T09:00:00Z"),
                ),
            ),
        )
        val subject = RoomHeroResourcesRepository(
            dao = dao,
            transactionRunner = ImmediateTransactionRunner,
        )

        // When
        val actual = subject.spendAmount(
            heroId = "player",
            resourceTypeId = resourceTypeId,
            amount = 3,
            updatedAt = updatedAt,
        )

        // Then
        actual shouldBe null
        subject.getAmount(heroId = "player", resourceTypeId = resourceTypeId) shouldBe 2
    }

    private object ImmediateTransactionRunner : TransactionRunner {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }

    private class FakeHeroResourceDao(
        initialEntities: List<HeroResourceEntity>,
    ) : HeroResourceDao {
        private val entities = MutableStateFlow(
            initialEntities.associateBy { Key(heroId = it.heroId, resourceTypeId = it.typeId) },
        )

        override fun observeAll(heroId: String): Flow<List<HeroResourceEntity>> =
            entities.map { map ->
                map.values
                    .filter { it.heroId == heroId && it.amount > 0 }
                    .sortedBy { it.typeId }
            }

        override fun observeAmount(
            heroId: String,
            typeId: String,
        ): Flow<Int?> =
            entities.map { map -> map[Key(heroId = heroId, resourceTypeId = typeId)]?.amount }

        override suspend fun getAmount(
            heroId: String,
            typeId: String,
        ): Int? =
            entities.value[Key(heroId = heroId, resourceTypeId = typeId)]?.amount

        override suspend fun getById(
            heroId: String,
            typeId: String,
        ): HeroResourceEntity? =
            entities.value[Key(heroId = heroId, resourceTypeId = typeId)]

        override suspend fun upsert(entity: HeroResourceEntity) {
            entities.value = entities.value.toMutableMap().apply {
                this[Key(heroId = entity.heroId, resourceTypeId = entity.typeId)] = entity
            }
        }

        override suspend fun incrementAmount(
            heroId: String,
            typeId: String,
            amountDelta: Int,
            updatedAt: Instant,
        ) {
            val key = Key(heroId = heroId, resourceTypeId = typeId)
            val existing = entities.value[key]
            val next = HeroResourceEntity(
                heroId = heroId,
                typeId = typeId,
                amount = (existing?.amount ?: 0) + amountDelta,
                updatedAt = updatedAt,
            )
            upsert(next)
        }

        override suspend fun decrementAmountIfEnough(
            heroId: String,
            typeId: String,
            amountDelta: Int,
            updatedAt: Instant,
        ): Int {
            val key = Key(heroId = heroId, resourceTypeId = typeId)
            val existing = entities.value[key] ?: return 0
            if (existing.amount < amountDelta) {
                return 0
            }

            upsert(
                existing.copy(
                    amount = existing.amount - amountDelta,
                    updatedAt = updatedAt,
                ),
            )
            return 1
        }

        private data class Key(
            val heroId: String,
            val resourceTypeId: String,
        )
    }
}
