package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.TransactionRunner
import com.github.arhor.journey.domain.ExplorationTileRuntimeConfigHolder
import com.github.arhor.journey.domain.internal.WatchtowerBalance
import com.github.arhor.journey.domain.internal.tileAt
import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfig
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroEnergy
import com.github.arhor.journey.domain.model.HeroResource
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.Progression
import com.github.arhor.journey.domain.model.WatchtowerDefinition
import com.github.arhor.journey.domain.model.WatchtowerRecord
import com.github.arhor.journey.domain.model.WatchtowerState
import com.github.arhor.journey.domain.model.error.ClaimWatchtowerError
import com.github.arhor.journey.domain.model.error.UpgradeWatchtowerError
import com.github.arhor.journey.domain.repository.HeroInventoryRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import com.github.arhor.journey.domain.repository.WatchtowerRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class WatchtowerUseCaseTest {

    @Test
    fun `discover should mark only undiscovered watchtowers whose center tile was newly cleared`() = runTest {
        // Given
        val runtimeConfig = ExplorationTileRuntimeConfig(canonicalZoom = 16, revealRadiusMeters = 50.0)
        val eligibleTower = watchtowerRecord(
            id = "watchtower:v1:15:17635:10747",
            location = GeoPoint(lat = 51.1093, lon = 17.0326),
            state = null,
        )
        val alreadyKnownTower = watchtowerRecord(
            id = "watchtower:v1:15:17636:10747",
            location = GeoPoint(lat = 51.1148, lon = 17.0461),
            state = watchtowerState(id = "watchtower:v1:15:17636:10747"),
        )
        val untouchedTower = watchtowerRecord(
            id = "watchtower:v1:15:17640:10750",
            location = GeoPoint(lat = 51.1067, lon = 17.0776),
            state = null,
        )
        val repository = FakeWatchtowerRepository(
            records = linkedMapOf(
                eligibleTower.definition.id to eligibleTower,
                alreadyKnownTower.definition.id to alreadyKnownTower,
                untouchedTower.definition.id to untouchedTower,
            ),
        )
        val subject = DiscoverWatchtowersByClearedTilesUseCase(
            repository = repository,
            getExplorationTileRuntimeConfig = getExplorationTileRuntimeConfigUseCase(runtimeConfig),
            clock = Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), ZoneOffset.UTC),
        )
        val newlyClearedTiles = setOf(
            tileAt(eligibleTower.definition.location, runtimeConfig.canonicalZoom),
        )

        // When
        val actual = subject(newlyClearedTiles)

        // Then
        actual shouldBe setOf("watchtower:v1:15:17635:10747")
        repository.markedDiscoveredIds shouldBe listOf("watchtower:v1:15:17635:10747")
    }

    @Test
    fun `claim should spend scrap and mark the watchtower claimed when requirements are met`() = runTest {
        // Given
        val now = Instant.parse("2026-04-01T10:00:00Z")
        val hero = hero()
        val actorLocation = GeoPoint(lat = 51.1093, lon = 17.0326)
        val repository = FakeWatchtowerRepository(
            records = linkedMapOf(
                "watchtower:v1:15:17635:10747" to watchtowerRecord(
                    id = "watchtower:v1:15:17635:10747",
                    location = actorLocation,
                    state = watchtowerState(id = "watchtower:v1:15:17635:10747", claimedAt = null, level = 0),
                ),
                "watchtower:v1:15:17635:10748" to watchtowerRecord(
                    id = "watchtower:v1:15:17635:10748",
                    location = actorLocation,
                    state = null,
                ),
            ),
        )
        val inventoryRepository = FakeHeroInventoryRepository(
            heroId = hero.id,
            initialAmounts = mutableMapOf("scrap" to 10, "components" to 10),
        )
        val subject = ClaimWatchtowerUseCase(
            heroRepository = FakeHeroRepository(hero),
            heroInventoryRepository = inventoryRepository,
            watchtowerRepository = repository,
            transactionRunner = RecordingTransactionRunner(),
            getExplorationTileRuntimeConfig = getExplorationTileRuntimeConfigUseCase(ExplorationTileRuntimeConfig()),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        // When
        val actual = subject(
            id = "watchtower:v1:15:17635:10747",
            actorLocation = actorLocation,
        )

        // Then
        actual shouldBe Output.Success(
            watchtowerState(
                id = "watchtower:v1:15:17635:10747",
                claimedAt = now,
                level = 1,
                updatedAt = now,
            ),
        )
        repository.getById("watchtower:v1:15:17635:10747")?.state?.level shouldBe 1
        inventoryRepository.getAmount(hero.id, "scrap") shouldBe 5
        repository.markedDiscoveredIds shouldBe listOf("watchtower:v1:15:17635:10748")
    }

    @Test
    fun `claim should return not found failure when watchtower does not exist`() = runTest {
        // Given
        val hero = hero()
        val actorLocation = GeoPoint(lat = 51.1093, lon = 17.0326)
        val subject = ClaimWatchtowerUseCase(
            heroRepository = FakeHeroRepository(hero),
            heroInventoryRepository = FakeHeroInventoryRepository(
                heroId = hero.id,
                initialAmounts = mutableMapOf("scrap" to 10),
            ),
            watchtowerRepository = FakeWatchtowerRepository(records = linkedMapOf()),
            transactionRunner = RecordingTransactionRunner(),
            getExplorationTileRuntimeConfig = getExplorationTileRuntimeConfigUseCase(ExplorationTileRuntimeConfig()),
            clock = Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), ZoneOffset.UTC),
        )

        // When
        val actual = subject(
            id = "missing-watchtower",
            actorLocation = actorLocation,
        )

        // Then
        actual shouldBe Output.Failure(ClaimWatchtowerError.NotFound("missing-watchtower"))
    }

    @Test
    fun `claim should return not discovered failure when watchtower state is missing`() = runTest {
        // Given
        val hero = hero()
        val actorLocation = GeoPoint(lat = 51.1093, lon = 17.0326)
        val repository = FakeWatchtowerRepository(
            records = linkedMapOf(
                "watchtower:v1:15:17635:10747" to watchtowerRecord(
                    id = "watchtower:v1:15:17635:10747",
                    location = actorLocation,
                    state = null,
                ),
            ),
        )
        val subject = ClaimWatchtowerUseCase(
            heroRepository = FakeHeroRepository(hero),
            heroInventoryRepository = FakeHeroInventoryRepository(
                heroId = hero.id,
                initialAmounts = mutableMapOf("scrap" to 10),
            ),
            watchtowerRepository = repository,
            transactionRunner = RecordingTransactionRunner(),
            getExplorationTileRuntimeConfig = getExplorationTileRuntimeConfigUseCase(ExplorationTileRuntimeConfig()),
            clock = Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), ZoneOffset.UTC),
        )

        // When
        val actual = subject(
            id = "watchtower:v1:15:17635:10747",
            actorLocation = actorLocation,
        )

        // Then
        actual shouldBe Output.Failure(ClaimWatchtowerError.NotDiscovered("watchtower:v1:15:17635:10747"))
    }

    @Test
    fun `claim should return already claimed failure when watchtower is already claimed`() = runTest {
        // Given
        val hero = hero()
        val actorLocation = GeoPoint(lat = 51.1093, lon = 17.0326)
        val repository = FakeWatchtowerRepository(
            records = linkedMapOf(
                "watchtower:v1:15:17635:10747" to watchtowerRecord(
                    id = "watchtower:v1:15:17635:10747",
                    location = actorLocation,
                    state = watchtowerState(
                        id = "watchtower:v1:15:17635:10747",
                        claimedAt = Instant.parse("2026-03-31T10:00:00Z"),
                        level = 1,
                    ),
                ),
            ),
        )
        val subject = ClaimWatchtowerUseCase(
            heroRepository = FakeHeroRepository(hero),
            heroInventoryRepository = FakeHeroInventoryRepository(
                heroId = hero.id,
                initialAmounts = mutableMapOf("scrap" to 10),
            ),
            watchtowerRepository = repository,
            transactionRunner = RecordingTransactionRunner(),
            getExplorationTileRuntimeConfig = getExplorationTileRuntimeConfigUseCase(ExplorationTileRuntimeConfig()),
            clock = Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), ZoneOffset.UTC),
        )

        // When
        val actual = subject(
            id = "watchtower:v1:15:17635:10747",
            actorLocation = actorLocation,
        )

        // Then
        actual shouldBe Output.Failure(ClaimWatchtowerError.AlreadyClaimed("watchtower:v1:15:17635:10747"))
    }

    @Test
    fun `claim should return not in range failure when actor is outside interaction radius`() = runTest {
        // Given
        val hero = hero()
        val actorLocation = GeoPoint(lat = 51.1093, lon = 17.0326)
        val repository = FakeWatchtowerRepository(
            records = linkedMapOf(
                "watchtower:v1:15:17635:10747" to watchtowerRecord(
                    id = "watchtower:v1:15:17635:10747",
                    location = GeoPoint(lat = 51.1193, lon = 17.0326),
                    state = watchtowerState(id = "watchtower:v1:15:17635:10747", claimedAt = null, level = 0),
                    interactionRadiusMeters = 25.0,
                ),
            ),
        )
        val subject = ClaimWatchtowerUseCase(
            heroRepository = FakeHeroRepository(hero),
            heroInventoryRepository = FakeHeroInventoryRepository(
                heroId = hero.id,
                initialAmounts = mutableMapOf("scrap" to 10),
            ),
            watchtowerRepository = repository,
            transactionRunner = RecordingTransactionRunner(),
            getExplorationTileRuntimeConfig = getExplorationTileRuntimeConfigUseCase(ExplorationTileRuntimeConfig()),
            clock = Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), ZoneOffset.UTC),
        )

        // When
        val actual = subject(
            id = "watchtower:v1:15:17635:10747",
            actorLocation = actorLocation,
        )

        // Then
        val failure = actual as Output.Failure<ClaimWatchtowerError>
        val error = failure.error as ClaimWatchtowerError.NotInRange
        error.watchtowerId shouldBe "watchtower:v1:15:17635:10747"
        (error.distanceMeters > error.interactionRadiusMeters) shouldBe true
        error.interactionRadiusMeters shouldBe 25.0
    }

    @Test
    fun `claim should return insufficient resources failure when hero lacks claim cost`() = runTest {
        // Given
        val hero = hero()
        val actorLocation = GeoPoint(lat = 51.1093, lon = 17.0326)
        val repository = FakeWatchtowerRepository(
            records = linkedMapOf(
                "watchtower:v1:15:17635:10747" to watchtowerRecord(
                    id = "watchtower:v1:15:17635:10747",
                    location = actorLocation,
                    state = watchtowerState(id = "watchtower:v1:15:17635:10747", claimedAt = null, level = 0),
                ),
            ),
        )
        val subject = ClaimWatchtowerUseCase(
            heroRepository = FakeHeroRepository(hero),
            heroInventoryRepository = FakeHeroInventoryRepository(
                heroId = hero.id,
                initialAmounts = mutableMapOf("scrap" to 4),
            ),
            watchtowerRepository = repository,
            transactionRunner = RecordingTransactionRunner(),
            getExplorationTileRuntimeConfig = getExplorationTileRuntimeConfigUseCase(ExplorationTileRuntimeConfig()),
            clock = Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), ZoneOffset.UTC),
        )

        // When
        val actual = subject(
            id = "watchtower:v1:15:17635:10747",
            actorLocation = actorLocation,
        )

        // Then
        actual shouldBe Output.Failure(
            ClaimWatchtowerError.InsufficientResources(
                watchtowerId = "watchtower:v1:15:17635:10747",
                resourceTypeId = "scrap",
                requiredAmount = 5,
                availableAmount = 4,
            ),
        )
    }

    @Test
    fun `upgrade should spend components and raise the watchtower level when requirements are met`() = runTest {
        // Given
        val now = Instant.parse("2026-04-01T10:00:00Z")
        val hero = hero()
        val actorLocation = GeoPoint(lat = 51.1093, lon = 17.0326)
        val repository = FakeWatchtowerRepository(
            records = linkedMapOf(
                "watchtower:v1:15:17635:10747" to watchtowerRecord(
                    id = "watchtower:v1:15:17635:10747",
                    location = actorLocation,
                    state = watchtowerState(
                        id = "watchtower:v1:15:17635:10747",
                        claimedAt = Instant.parse("2026-03-31T10:00:00Z"),
                        level = 1,
                    ),
                ),
                "watchtower:v1:15:17635:10748" to watchtowerRecord(
                    id = "watchtower:v1:15:17635:10748",
                    location = actorLocation,
                    state = null,
                ),
            ),
        )
        val inventoryRepository = FakeHeroInventoryRepository(
            heroId = hero.id,
            initialAmounts = mutableMapOf("scrap" to 10, "components" to 20),
        )
        val subject = UpgradeWatchtowerUseCase(
            heroRepository = FakeHeroRepository(hero),
            heroInventoryRepository = inventoryRepository,
            watchtowerRepository = repository,
            transactionRunner = RecordingTransactionRunner(),
            getExplorationTileRuntimeConfig = getExplorationTileRuntimeConfigUseCase(ExplorationTileRuntimeConfig()),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        // When
        val actual = subject(
            id = "watchtower:v1:15:17635:10747",
            actorLocation = actorLocation,
        )

        // Then
        actual shouldBe Output.Success(
            watchtowerState(
                id = "watchtower:v1:15:17635:10747",
                claimedAt = Instant.parse("2026-03-31T10:00:00Z"),
                level = 2,
                updatedAt = now,
            ),
        )
        repository.getById("watchtower:v1:15:17635:10747")?.state?.level shouldBe 2
        inventoryRepository.getAmount(hero.id, "components") shouldBe 10
        repository.markedDiscoveredIds shouldBe listOf("watchtower:v1:15:17635:10748")
    }

    @Test
    fun `upgrade should return not found failure when watchtower does not exist`() = runTest {
        // Given
        val hero = hero()
        val actorLocation = GeoPoint(lat = 51.1093, lon = 17.0326)
        val subject = UpgradeWatchtowerUseCase(
            heroRepository = FakeHeroRepository(hero),
            heroInventoryRepository = FakeHeroInventoryRepository(
                heroId = hero.id,
                initialAmounts = mutableMapOf("components" to 20),
            ),
            watchtowerRepository = FakeWatchtowerRepository(records = linkedMapOf()),
            transactionRunner = RecordingTransactionRunner(),
            getExplorationTileRuntimeConfig = getExplorationTileRuntimeConfigUseCase(ExplorationTileRuntimeConfig()),
            clock = Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), ZoneOffset.UTC),
        )

        // When
        val actual = subject(
            id = "missing-watchtower",
            actorLocation = actorLocation,
        )

        // Then
        actual shouldBe Output.Failure(UpgradeWatchtowerError.NotFound("missing-watchtower"))
    }

    @Test
    fun `upgrade should return not claimed failure when watchtower is unclaimed`() = runTest {
        // Given
        val hero = hero()
        val actorLocation = GeoPoint(lat = 51.1093, lon = 17.0326)
        val repository = FakeWatchtowerRepository(
            records = linkedMapOf(
                "watchtower:v1:15:17635:10747" to watchtowerRecord(
                    id = "watchtower:v1:15:17635:10747",
                    location = actorLocation,
                    state = watchtowerState(id = "watchtower:v1:15:17635:10747", claimedAt = null, level = 0),
                ),
            ),
        )
        val subject = UpgradeWatchtowerUseCase(
            heroRepository = FakeHeroRepository(hero),
            heroInventoryRepository = FakeHeroInventoryRepository(
                heroId = hero.id,
                initialAmounts = mutableMapOf("components" to 20),
            ),
            watchtowerRepository = repository,
            transactionRunner = RecordingTransactionRunner(),
            getExplorationTileRuntimeConfig = getExplorationTileRuntimeConfigUseCase(ExplorationTileRuntimeConfig()),
            clock = Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), ZoneOffset.UTC),
        )

        // When
        val actual = subject(
            id = "watchtower:v1:15:17635:10747",
            actorLocation = actorLocation,
        )

        // Then
        actual shouldBe Output.Failure(UpgradeWatchtowerError.NotClaimed("watchtower:v1:15:17635:10747"))
    }

    @Test
    fun `upgrade should return max level failure when watchtower is already maxed`() = runTest {
        // Given
        val hero = hero()
        val actorLocation = GeoPoint(lat = 51.1093, lon = 17.0326)
        val repository = FakeWatchtowerRepository(
            records = linkedMapOf(
                "watchtower:v1:15:17635:10747" to watchtowerRecord(
                    id = "watchtower:v1:15:17635:10747",
                    location = actorLocation,
                    state = watchtowerState(
                        id = "watchtower:v1:15:17635:10747",
                        claimedAt = Instant.parse("2026-03-31T10:00:00Z"),
                        level = WatchtowerBalance.MAX_LEVEL,
                    ),
                ),
            ),
        )
        val subject = UpgradeWatchtowerUseCase(
            heroRepository = FakeHeroRepository(hero),
            heroInventoryRepository = FakeHeroInventoryRepository(
                heroId = hero.id,
                initialAmounts = mutableMapOf("components" to 20),
            ),
            watchtowerRepository = repository,
            transactionRunner = RecordingTransactionRunner(),
            getExplorationTileRuntimeConfig = getExplorationTileRuntimeConfigUseCase(ExplorationTileRuntimeConfig()),
            clock = Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), ZoneOffset.UTC),
        )

        // When
        val actual = subject(
            id = "watchtower:v1:15:17635:10747",
            actorLocation = actorLocation,
        )

        // Then
        actual shouldBe Output.Failure(UpgradeWatchtowerError.AlreadyAtMaxLevel("watchtower:v1:15:17635:10747"))
    }

    @Test
    fun `upgrade should return not in range failure when actor is outside interaction radius`() = runTest {
        // Given
        val hero = hero()
        val actorLocation = GeoPoint(lat = 51.1093, lon = 17.0326)
        val repository = FakeWatchtowerRepository(
            records = linkedMapOf(
                "watchtower:v1:15:17635:10747" to watchtowerRecord(
                    id = "watchtower:v1:15:17635:10747",
                    location = GeoPoint(lat = 51.1193, lon = 17.0326),
                    state = watchtowerState(
                        id = "watchtower:v1:15:17635:10747",
                        claimedAt = Instant.parse("2026-03-31T10:00:00Z"),
                        level = 1,
                    ),
                    interactionRadiusMeters = 25.0,
                ),
            ),
        )
        val subject = UpgradeWatchtowerUseCase(
            heroRepository = FakeHeroRepository(hero),
            heroInventoryRepository = FakeHeroInventoryRepository(
                heroId = hero.id,
                initialAmounts = mutableMapOf("components" to 20),
            ),
            watchtowerRepository = repository,
            transactionRunner = RecordingTransactionRunner(),
            getExplorationTileRuntimeConfig = getExplorationTileRuntimeConfigUseCase(ExplorationTileRuntimeConfig()),
            clock = Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), ZoneOffset.UTC),
        )

        // When
        val actual = subject(
            id = "watchtower:v1:15:17635:10747",
            actorLocation = actorLocation,
        )

        // Then
        val failure = actual as Output.Failure<UpgradeWatchtowerError>
        val error = failure.error as UpgradeWatchtowerError.NotInRange
        error.watchtowerId shouldBe "watchtower:v1:15:17635:10747"
        (error.distanceMeters > error.interactionRadiusMeters) shouldBe true
        error.interactionRadiusMeters shouldBe 25.0
    }

    @Test
    fun `upgrade should return insufficient resources failure when hero lacks upgrade cost`() = runTest {
        // Given
        val hero = hero()
        val actorLocation = GeoPoint(lat = 51.1093, lon = 17.0326)
        val repository = FakeWatchtowerRepository(
            records = linkedMapOf(
                "watchtower:v1:15:17635:10747" to watchtowerRecord(
                    id = "watchtower:v1:15:17635:10747",
                    location = actorLocation,
                    state = watchtowerState(
                        id = "watchtower:v1:15:17635:10747",
                        claimedAt = Instant.parse("2026-03-31T10:00:00Z"),
                        level = 1,
                    ),
                ),
            ),
        )
        val subject = UpgradeWatchtowerUseCase(
            heroRepository = FakeHeroRepository(hero),
            heroInventoryRepository = FakeHeroInventoryRepository(
                heroId = hero.id,
                initialAmounts = mutableMapOf("components" to 9),
            ),
            watchtowerRepository = repository,
            transactionRunner = RecordingTransactionRunner(),
            getExplorationTileRuntimeConfig = getExplorationTileRuntimeConfigUseCase(ExplorationTileRuntimeConfig()),
            clock = Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), ZoneOffset.UTC),
        )

        // When
        val actual = subject(
            id = "watchtower:v1:15:17635:10747",
            actorLocation = actorLocation,
        )

        // Then
        actual shouldBe Output.Failure(
            UpgradeWatchtowerError.InsufficientResources(
                watchtowerId = "watchtower:v1:15:17635:10747",
                resourceTypeId = "components",
                requiredAmount = 10,
                availableAmount = 9,
            ),
        )
    }

    private fun hero(): Hero =
        Hero(
            id = "player",
            name = "Adventurer",
            progression = Progression(level = 1, xpInLevel = 0L),
            energy = HeroEnergy(max = 100),
            createdAt = Instant.parse("2026-04-01T09:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T09:00:00Z"),
        )

    private fun watchtowerRecord(
        id: String,
        location: GeoPoint,
        state: WatchtowerState?,
        interactionRadiusMeters: Double = 25.0,
    ): WatchtowerRecord =
        WatchtowerRecord(
            definition = WatchtowerDefinition(
                id = id,
                name = id,
                description = null,
                location = location,
                interactionRadiusMeters = interactionRadiusMeters,
            ),
            state = state,
        )

    private fun watchtowerState(
        id: String,
        discoveredAt: Instant = Instant.parse("2026-03-31T08:00:00Z"),
        claimedAt: Instant? = null,
        level: Int = 0,
        updatedAt: Instant = discoveredAt,
    ): WatchtowerState =
        WatchtowerState(
            watchtowerId = id,
            discoveredAt = discoveredAt,
            claimedAt = claimedAt,
            level = level,
            updatedAt = updatedAt,
        )

    private fun getExplorationTileRuntimeConfigUseCase(
        config: ExplorationTileRuntimeConfig,
    ): GetExplorationTileRuntimeConfigUseCase {
        val holder = ExplorationTileRuntimeConfigHolder().apply {
            setCanonicalZoom(config.canonicalZoom)
            setRevealRadiusMeters(config.revealRadiusMeters)
        }
        return GetExplorationTileRuntimeConfigUseCase(holder)
    }

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
        private val heroId: String,
        private val initialAmounts: MutableMap<String, Int>,
    ) : HeroInventoryRepository {
        override fun observeAll(heroId: String): Flow<List<HeroResource>> = emptyFlow()

        override fun observeAmount(
            heroId: String,
            resourceTypeId: String,
        ): Flow<Int> = MutableStateFlow(initialAmounts[resourceTypeId] ?: 0)

        override suspend fun getAmount(
            heroId: String,
            resourceTypeId: String,
        ): Int = initialAmounts[resourceTypeId] ?: 0

        override suspend fun setAmount(
            heroId: String,
            resourceTypeId: String,
            amount: Int,
            updatedAt: Instant,
        ): HeroResource {
            initialAmounts[resourceTypeId] = amount
            return heroResource(resourceTypeId, amount, updatedAt)
        }

        override suspend fun addAmount(
            heroId: String,
            resourceTypeId: String,
            amount: Int,
            updatedAt: Instant,
        ): HeroResource {
            val newAmount = (initialAmounts[resourceTypeId] ?: 0) + amount
            initialAmounts[resourceTypeId] = newAmount
            return heroResource(resourceTypeId, newAmount, updatedAt)
        }

        override suspend fun spendAmount(
            heroId: String,
            resourceTypeId: String,
            amount: Int,
            updatedAt: Instant,
        ): HeroResource? {
            val currentAmount = initialAmounts[resourceTypeId] ?: 0
            if (currentAmount < amount) {
                return null
            }

            val newAmount = currentAmount - amount
            initialAmounts[resourceTypeId] = newAmount
            return heroResource(resourceTypeId, newAmount, updatedAt)
        }

        private fun heroResource(
            resourceTypeId: String,
            amount: Int,
            updatedAt: Instant,
        ): HeroResource =
            HeroResource(
                heroId = heroId,
                resourceTypeId = resourceTypeId,
                amount = amount,
                updatedAt = updatedAt,
            )
    }

    private class FakeWatchtowerRepository(
        private val records: LinkedHashMap<String, WatchtowerRecord>,
    ) : WatchtowerRepository {
        val markedDiscoveredIds = mutableListOf<String>()

        override fun observeInBounds(bounds: GeoBounds): Flow<List<WatchtowerRecord>> =
            MutableStateFlow(
                records.values.filter { record ->
                    bounds.contains(record.definition.location)
                },
            )

        override suspend fun getInBounds(bounds: GeoBounds): List<WatchtowerRecord> =
            records.values.filter { record ->
                bounds.contains(record.definition.location)
            }

        override suspend fun getIntersectingTiles(tiles: Set<MapTile>): List<WatchtowerRecord> =
            records.values.filter { record ->
                tiles.any { tile ->
                    tileAt(
                        point = record.definition.location,
                        zoom = tile.zoom,
                    ) == tile
                }
            }

        override suspend fun getById(id: String): WatchtowerRecord? = records[id]

        override suspend fun markDiscovered(
            id: String,
            discoveredAt: Instant,
        ): Boolean {
            val current = records[id] ?: return false
            if (current.state != null) {
                return false
            }

            records[id] = current.copy(
                state = WatchtowerState(
                    watchtowerId = id,
                    discoveredAt = discoveredAt,
                    claimedAt = null,
                    level = 0,
                    updatedAt = discoveredAt,
                ),
            )
            markedDiscoveredIds += id
            return true
        }

        override suspend fun markClaimed(
            id: String,
            claimedAt: Instant,
            level: Int,
            updatedAt: Instant,
        ): Boolean {
            val current = records[id] ?: return false
            val currentState = current.state ?: return false
            if (currentState.claimedAt != null) {
                return false
            }

            records[id] = current.copy(
                state = currentState.copy(
                    claimedAt = claimedAt,
                    level = level,
                    updatedAt = updatedAt,
                ),
            )
            return true
        }

        override suspend fun setLevel(
            id: String,
            level: Int,
            updatedAt: Instant,
        ): Boolean {
            val current = records[id] ?: return false
            val currentState = current.state ?: return false
            if (currentState.claimedAt == null || currentState.level >= level) {
                return false
            }

            records[id] = current.copy(
                state = currentState.copy(
                    level = level,
                    updatedAt = updatedAt,
                ),
            )
            return true
        }
    }

    private class RecordingTransactionRunner : TransactionRunner {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
}
