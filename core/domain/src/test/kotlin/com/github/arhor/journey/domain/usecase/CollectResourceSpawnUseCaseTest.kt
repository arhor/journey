package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.TransactionRunner
import com.github.arhor.journey.domain.model.CollectedResourceSpawn
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroEnergy
import com.github.arhor.journey.domain.model.HeroResource
import com.github.arhor.journey.domain.model.Progression
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.ResourceSpawnCollectionResult
import com.github.arhor.journey.domain.repository.CollectedResourceSpawnRepository
import com.github.arhor.journey.domain.repository.HeroInventoryRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import com.github.arhor.journey.domain.repository.ResourceSpawnRepository
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CollectResourceSpawnUseCaseTest {

    @Test
    fun `invoke should mark the spawn collected and award inventory when spawn is nearby`() = runTest {
        // Given
        val now = Instant.parse("2026-03-19T10:00:00Z")
        val hero = hero()
        val transactionRunner = RecordingTransactionRunner()
        val collectedRepository = FakeCollectedResourceSpawnRepository(
            isTransactionOpen = { transactionRunner.isInTransaction },
        )
        val inventoryRepository = FakeHeroInventoryRepository(
            isTransactionOpen = { transactionRunner.isInTransaction },
        )
        val spawn = resourceSpawn(
            id = "resource-spawn:v1:20527:10:20:0:wood",
            position = GeoPoint(lat = 49.0000, lon = 24.0000),
            resourceTypeId = "wood",
            radiusMeters = 25.0,
        )
        val subject = CollectResourceSpawnUseCase(
            heroRepository = FakeHeroRepository(hero),
            heroInventoryRepository = inventoryRepository,
            collectedResourceSpawnRepository = collectedRepository,
            resourceSpawnRepository = FakeResourceSpawnRepository(spawn),
            transactionRunner = transactionRunner,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        // When
        val actual = subject(
            spawnId = spawn.id,
            collectorLocation = GeoPoint(lat = 49.0000, lon = 24.0000),
        )

        // Then
        actual shouldBe ResourceSpawnCollectionResult.Collected(
            spawnId = spawn.id,
            resourceTypeId = "wood",
            amountAwarded = 1,
        )
        collectedRepository.markedCollectedSpawns.map { it.spawnId } shouldContainExactly listOf(spawn.id)
        inventoryRepository.addedResources shouldContainExactly listOf(
            HeroResource(
                heroId = hero.id,
                resourceTypeId = "wood",
                amount = 1,
                updatedAt = now,
            ),
        )
        transactionRunner.runCount shouldBe 1
        collectedRepository.wasMutationRecordedInsideTransaction shouldBe true
        inventoryRepository.wasMutationRecordedInsideTransaction shouldBe true
    }

    @Test
    fun `invoke should stay idempotent when spawn was already collected by the hero`() = runTest {
        // Given
        val now = Instant.parse("2026-03-19T10:00:00Z")
        val hero = hero()
        val spawnId = "resource-spawn:v1:20527:10:20:0:wood"
        val collectedRepository = FakeCollectedResourceSpawnRepository(
            initialClaims = listOf(
                CollectedResourceSpawn(
                    heroId = hero.id,
                    typeId = "wood",
                    spawnId = spawnId,
                    collectedAt = now.minusSeconds(60),
                ),
            ),
        )
        val inventoryRepository = FakeHeroInventoryRepository()
        val subject = CollectResourceSpawnUseCase(
            heroRepository = FakeHeroRepository(hero),
            heroInventoryRepository = inventoryRepository,
            collectedResourceSpawnRepository = collectedRepository,
            resourceSpawnRepository = FakeResourceSpawnRepository(
                resourceSpawn(
                    id = spawnId,
                    position = GeoPoint(lat = 49.0000, lon = 24.0000),
                    resourceTypeId = "wood",
                    radiusMeters = 25.0,
                ),
            ),
            transactionRunner = RecordingTransactionRunner(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        // When
        val actual = subject(
            spawnId = spawnId,
            collectorLocation = GeoPoint(lat = 49.0000, lon = 24.0000),
        )

        // Then
        actual shouldBe ResourceSpawnCollectionResult.AlreadyCollected(spawnId)
        inventoryRepository.addedResources shouldBe emptyList()
    }

    @Test
    fun `invoke should return not close enough when collector is outside the spawn radius`() = runTest {
        // Given
        val now = Instant.parse("2026-03-19T10:00:00Z")
        val transactionRunner = RecordingTransactionRunner()
        val spawn = resourceSpawn(
            id = "resource-spawn:v1:20527:10:20:0:stone",
            position = GeoPoint(lat = 49.0000, lon = 24.0000),
            resourceTypeId = "stone",
            radiusMeters = 10.0,
        )
        val subject = CollectResourceSpawnUseCase(
            heroRepository = FakeHeroRepository(hero()),
            heroInventoryRepository = FakeHeroInventoryRepository(),
            collectedResourceSpawnRepository = FakeCollectedResourceSpawnRepository(),
            resourceSpawnRepository = FakeResourceSpawnRepository(spawn),
            transactionRunner = transactionRunner,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        // When
        val actual = subject(
            spawnId = spawn.id,
            collectorLocation = GeoPoint(lat = 49.0003, lon = 24.0000),
        )

        // Then
        actual as ResourceSpawnCollectionResult.NotCloseEnough
        actual.spawnId shouldBe spawn.id
        actual.collectionRadiusMeters shouldBe 10.0
        (actual.distanceMeters > actual.collectionRadiusMeters) shouldBe true
        transactionRunner.runCount shouldBe 0
    }

    @Test
    fun `invoke should return not found when active spawn is unavailable`() = runTest {
        // Given
        val subject = CollectResourceSpawnUseCase(
            heroRepository = FakeHeroRepository(hero()),
            heroInventoryRepository = FakeHeroInventoryRepository(),
            collectedResourceSpawnRepository = FakeCollectedResourceSpawnRepository(),
            resourceSpawnRepository = FakeResourceSpawnRepository(),
            transactionRunner = RecordingTransactionRunner(),
            clock = Clock.fixed(Instant.parse("2026-03-19T10:00:00Z"), ZoneOffset.UTC),
        )

        // When
        val actual = subject(
            spawnId = "missing-spawn",
            collectorLocation = GeoPoint(lat = 49.0000, lon = 24.0000),
        )

        // Then
        actual shouldBe ResourceSpawnCollectionResult.NotFound("missing-spawn")
    }

    @Test
    fun `invoke should mark the spawn collected and award inside a single transaction boundary`() = runTest {
        // Given
        val now = Instant.parse("2026-03-19T10:00:00Z")
        val transactionRunner = RecordingTransactionRunner()
        val collectedRepository = FakeCollectedResourceSpawnRepository(
            isTransactionOpen = { transactionRunner.isInTransaction },
        )
        val inventoryRepository = FakeHeroInventoryRepository(
            isTransactionOpen = { transactionRunner.isInTransaction },
        )
        val subject = CollectResourceSpawnUseCase(
            heroRepository = FakeHeroRepository(hero()),
            heroInventoryRepository = inventoryRepository,
            collectedResourceSpawnRepository = collectedRepository,
            resourceSpawnRepository = FakeResourceSpawnRepository(
                resourceSpawn(
                    id = "resource-spawn:v1:20527:10:20:1:coal",
                    position = GeoPoint(lat = 49.0000, lon = 24.0000),
                    resourceTypeId = "coal",
                    radiusMeters = 25.0,
                ),
            ),
            transactionRunner = transactionRunner,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        // When
        subject(
            spawnId = "resource-spawn:v1:20527:10:20:1:coal",
            collectorLocation = GeoPoint(lat = 49.0000, lon = 24.0000),
        )

        // Then
        transactionRunner.runCount shouldBe 1
        collectedRepository.wasMutationRecordedInsideTransaction shouldBe true
        inventoryRepository.wasMutationRecordedInsideTransaction shouldBe true
    }

    private fun hero(): Hero =
        Hero(
            id = "player",
            name = "Adventurer",
            progression = Progression(level = 1, xpInLevel = 0L),
            energy = HeroEnergy(max = 100),
            createdAt = Instant.parse("2026-03-19T09:00:00Z"),
            updatedAt = Instant.parse("2026-03-19T09:00:00Z"),
        )

    private fun resourceSpawn(
        id: String,
        position: GeoPoint,
        resourceTypeId: String,
        radiusMeters: Double,
    ): ResourceSpawn =
        ResourceSpawn(
            id = id,
            typeId = resourceTypeId,
            position = position,
            collectionRadiusMeters = radiusMeters,
            availableFrom = Instant.parse("2026-03-19T00:00:00Z"),
            availableUntil = Instant.parse("2026-03-20T00:00:00Z"),
        )

    private class FakeHeroRepository(
        hero: Hero,
    ) : HeroRepository {
        private val flow = MutableStateFlow(hero)

        override fun observeCurrentHero(): Flow<Hero> = flow

        override suspend fun getCurrentHero(): Hero = flow.value

        override suspend fun upsert(hero: Hero) {
            flow.value = hero
        }
    }

    private class FakeHeroInventoryRepository(
        private val isTransactionOpen: () -> Boolean = { false },
    ) : HeroInventoryRepository {
        val addedResources = mutableListOf<HeroResource>()
        var wasMutationRecordedInsideTransaction: Boolean = false

        override fun observeAll(heroId: String): Flow<List<HeroResource>> = emptyFlow()

        override fun observeAmount(
            heroId: String,
            resourceTypeId: String,
        ): Flow<Int> = emptyFlow()

        override suspend fun getAmount(
            heroId: String,
            resourceTypeId: String,
        ): Int = addedResources
            .firstOrNull { it.heroId == heroId && it.resourceTypeId == resourceTypeId }
            ?.amount
            ?: 0

        override suspend fun setAmount(
            heroId: String,
            resourceTypeId: String,
            amount: Int,
            updatedAt: Instant,
        ): HeroResource = throw NotImplementedError()

        override suspend fun addAmount(
            heroId: String,
            resourceTypeId: String,
            amount: Int,
            updatedAt: Instant,
        ): HeroResource {
            wasMutationRecordedInsideTransaction = isTransactionOpen()
            return HeroResource(
                heroId = heroId,
                resourceTypeId = resourceTypeId,
                amount = amount,
                updatedAt = updatedAt,
            ).also(addedResources::add)
        }

        override suspend fun spendAmount(
            heroId: String,
            resourceTypeId: String,
            amount: Int,
            updatedAt: Instant,
        ): HeroResource? = throw NotImplementedError()
    }

    private class FakeCollectedResourceSpawnRepository(
        initialClaims: List<CollectedResourceSpawn> = emptyList(),
        private val isTransactionOpen: () -> Boolean = { false },
    ) : CollectedResourceSpawnRepository {
        private val collectedSpawns = initialClaims
            .associateBy { it.heroId to it.spawnId }
            .toMutableMap()

        val markedCollectedSpawns = mutableListOf<CollectedResourceSpawn>()
        var wasMutationRecordedInsideTransaction: Boolean = false

        override fun observeAll(heroId: String): Flow<List<CollectedResourceSpawn>> = emptyFlow()

        override suspend fun isCollected(
            heroId: String,
            spawnId: String,
        ): Boolean = collectedSpawns.containsKey(heroId to spawnId)

        override suspend fun markCollected(
            heroId: String,
            spawnId: String,
            resourceTypeId: String,
            collectedAt: Instant,
        ): Boolean {
            wasMutationRecordedInsideTransaction = isTransactionOpen()

            val key = heroId to spawnId
            if (collectedSpawns.containsKey(key)) {
                return false
            }
            val collectedSpawn = CollectedResourceSpawn(
                heroId = heroId,
                typeId = resourceTypeId,
                spawnId = spawnId,
                collectedAt = collectedAt,
            )
            collectedSpawns[key] = collectedSpawn
            markedCollectedSpawns += collectedSpawn

            return true
        }
    }

    private class FakeResourceSpawnRepository(
        vararg initialSpawns: ResourceSpawn,
    ) : ResourceSpawnRepository {
        private val spawns = initialSpawns.associateBy(ResourceSpawn::id)

        override fun observeActiveSpawns(query: com.github.arhor.journey.domain.model.ResourceSpawnQuery): Flow<List<ResourceSpawn>> =
            emptyFlow()

        override suspend fun getActiveSpawns(query: com.github.arhor.journey.domain.model.ResourceSpawnQuery): List<ResourceSpawn> =
            spawns.values.toList()

        override suspend fun getActiveSpawn(
            spawnId: String,
            at: Instant,
        ): ResourceSpawn? = spawns[spawnId]
    }

    private class RecordingTransactionRunner : TransactionRunner {
        var runCount: Int = 0
        var isInTransaction: Boolean = false

        override suspend fun <T> runInTransaction(block: suspend () -> T): T {
            runCount += 1
            isInTransaction = true
            return try {
                block()
            } finally {
                isInTransaction = false
            }
        }
    }
}
