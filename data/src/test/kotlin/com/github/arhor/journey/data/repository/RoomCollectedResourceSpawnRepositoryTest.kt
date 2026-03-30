package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.CollectedResourceSpawnDao
import com.github.arhor.journey.data.local.db.entity.CollectedResourceSpawnEntity
import com.github.arhor.journey.domain.model.CollectedResourceSpawn
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class RoomCollectedResourceSpawnRepositoryTest {

    @Test
    fun `observeAll should map collected spawn entities to domain models`() = runTest {
        // Given
        val collectedAt = Instant.parse("2026-03-11T06:00:00Z")
        val dao = FakeCollectedResourceSpawnDao(
            initialEntities = listOf(
                CollectedResourceSpawnEntity(
                    heroId = "player",
                    spawnId = "spawn-2",
                    typeId = "stone",
                    collectedAt = collectedAt,
                ),
            ),
        )
        val subject = RoomCollectedResourceSpawnRepository(dao = dao)

        // When
        val actual = subject.observeAll(heroId = "player").first()

        // Then
        actual shouldBe listOf(
            CollectedResourceSpawn(
                heroId = "player",
                spawnId = "spawn-2",
                typeId = "stone",
                collectedAt = collectedAt,
            ),
        )
    }

    @Test
    fun `markCollected should return false when the same hero spawn pair already exists`() = runTest {
        // Given
        val collectedAt = Instant.parse("2026-03-11T06:00:00Z")
        val dao = FakeCollectedResourceSpawnDao(
            initialEntities = listOf(
                CollectedResourceSpawnEntity(
                    heroId = "player",
                    spawnId = "spawn-7",
                    typeId = "wood",
                    collectedAt = collectedAt,
                ),
            ),
        )
        val subject = RoomCollectedResourceSpawnRepository(dao = dao)

        // When
        val actual = subject.markCollected(
            heroId = "player",
            spawnId = "spawn-7",
            resourceTypeId = "wood",
            collectedAt = Instant.parse("2026-03-11T06:05:00Z"),
        )

        // Then
        actual shouldBe false
        subject.isCollected(heroId = "player", spawnId = "spawn-7") shouldBe true
    }

    @Test
    fun `markCollected should persist the collected spawn when hero has not collected it yet`() = runTest {
        // Given
        val dao = FakeCollectedResourceSpawnDao(initialEntities = emptyList())
        val subject = RoomCollectedResourceSpawnRepository(dao = dao)
        val collectedAt = Instant.parse("2026-03-11T06:10:00Z")

        // When
        val actual = subject.markCollected(
            heroId = "player",
            spawnId = "spawn-8",
            resourceTypeId = "ore",
            collectedAt = collectedAt,
        )

        // Then
        actual shouldBe true
        subject.observeAll(heroId = "player").first() shouldBe listOf(
            CollectedResourceSpawn(
                heroId = "player",
                spawnId = "spawn-8",
                typeId = "ore",
                collectedAt = collectedAt,
            ),
        )
    }

    private class FakeCollectedResourceSpawnDao(
        initialEntities: List<CollectedResourceSpawnEntity>,
    ) : CollectedResourceSpawnDao {
        private val entities = MutableStateFlow(
            initialEntities.associateBy { Key(heroId = it.heroId, spawnId = it.spawnId) },
        )
        private var nextRowId: Long = 1L

        override fun observeAll(heroId: String): Flow<List<CollectedResourceSpawnEntity>> =
            entities.map { map ->
                map.values
                    .filter { it.heroId == heroId }
                    .sortedByDescending { it.collectedAt }
            }

        override suspend fun exists(
            heroId: String,
            spawnId: String,
        ): Boolean = entities.value.containsKey(Key(heroId = heroId, spawnId = spawnId))

        override suspend fun insert(entity: CollectedResourceSpawnEntity): Long {
            val key = Key(heroId = entity.heroId, spawnId = entity.spawnId)
            if (entities.value.containsKey(key)) {
                return -1L
            }

            entities.value = entities.value.toMutableMap().apply {
                this[key] = entity
            }

            return nextRowId++
        }

        private data class Key(
            val heroId: String,
            val spawnId: String,
        )
    }
}
