package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.DiscoveredPoiDao
import com.github.arhor.journey.data.local.db.entity.DiscoveredPoiEntity
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class RoomExplorationRepositoryTest {

    @Test
    fun `observeProgress should map discovered entities to set when dao emits duplicates`() = runTest {
        // Given
        val discoveredAt = Instant.parse("2026-02-20T08:30:00Z")
        val dao = FakeDiscoveredPoiDao(
            observedItems = listOf(
                DiscoveredPoiEntity(
                    poiId = "poi-1",
                    discoveredAt = discoveredAt,
                ),
                DiscoveredPoiEntity(
                    poiId = "poi-1",
                    discoveredAt = discoveredAt,
                ),
            ),
        )
        val subject = RoomExplorationRepository(dao = dao)

        // When
        val actual = subject.observeProgress().first()

        // Then
        actual.discovered shouldHaveSize 1
        actual.discovered.first().poiId shouldBe "poi-1"
    }

    @Test
    fun `discoverPoi should persist instant when poi discovery is persisted`() = runTest {
        // Given
        val dao = FakeDiscoveredPoiDao(observedItems = emptyList())
        val subject = RoomExplorationRepository(dao = dao)
        val discoveredAt = Instant.parse("2026-02-21T10:00:00Z")

        // When
        subject.discoverPoi(
            poiId = "poi-9",
            discoveredAt = discoveredAt,
        )

        // Then
        dao.insertedEntities shouldBe listOf(
            DiscoveredPoiEntity(
                poiId = "poi-9",
                discoveredAt = discoveredAt,
            ),
        )
    }

    private class FakeDiscoveredPoiDao(
        private val observedItems: List<DiscoveredPoiEntity>,
    ) : DiscoveredPoiDao {
        val insertedEntities = mutableListOf<DiscoveredPoiEntity>()

        override fun observeAll(): Flow<List<DiscoveredPoiEntity>> = flowOf(observedItems)

        override suspend fun insert(entity: DiscoveredPoiEntity): Long {
            insertedEntities += entity
            return 1L
        }
    }
}
