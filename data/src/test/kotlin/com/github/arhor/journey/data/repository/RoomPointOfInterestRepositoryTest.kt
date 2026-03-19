package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.PoiDao
import com.github.arhor.journey.data.local.db.entity.PoiEntity
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomPointOfInterestRepositoryTest {

    @Test
    fun `observeAll should map entities and fallback category when dao emits unknown value`() = runTest {
        // Given
        val knownPoiId = 1L
        val unknownPoiId = 2L
        val dao = FakePoiDao(
            countValue = 1,
            observedItems = listOf(
                PoiEntity(
                    id = knownPoiId,
                    name = "Known",
                    description = null,
                    category = "LANDMARK",
                    lat = 52.0,
                    lon = 21.0,
                    radiusMeters = 80,
                ),
                PoiEntity(
                    id = unknownPoiId,
                    name = "Unknown",
                    description = "Falls back to LANDMARK",
                    category = "NOT_EXISTING",
                    lat = 53.0,
                    lon = 20.0,
                    radiusMeters = 90,
                ),
            ),
        )
        val subject = RoomPointOfInterestRepository(dao = dao)

        // When
        val actual = subject.observeAll().firstValue()

        // Then
        actual shouldHaveSize 2
        actual.first().id shouldBe knownPoiId
        actual.last().category.name shouldBe "LANDMARK"
    }

    @Test
    fun `getById should map dao entity when point of interest exists`() = runTest {
        // Given
        val poiId = 3L
        val dao = FakePoiDao(
            countValue = 1,
            observedItems = listOf(
                PoiEntity(
                    id = poiId,
                    name = "Known",
                    description = "Known description",
                    category = "LANDMARK",
                    lat = 52.0,
                    lon = 21.0,
                    radiusMeters = 80,
                ),
            ),
        )
        val subject = RoomPointOfInterestRepository(dao = dao)

        // When
        val actual = subject.getById(poiId)

        // Then
        actual?.id shouldBe poiId
        actual?.name shouldBe "Known"
    }

    private suspend fun <T> Flow<T>.firstValue(): T = first()

    private class FakePoiDao(
        private val countValue: Int,
        private val observedItems: List<PoiEntity>,
    ) : PoiDao {
        var upsertedEntities: List<PoiEntity>? = null
        var upsertedEntity: PoiEntity? = null

        override fun observeAll(): Flow<List<PoiEntity>> = flowOf(observedItems)

        override suspend fun getById(id: Long): PoiEntity? = observedItems.find { it.id == id }

        override suspend fun count(): Int = countValue

        override suspend fun upsert(entity: PoiEntity): Long {
            upsertedEntity = entity
            return entity.id
        }

        override suspend fun upsertAll(entities: List<PoiEntity>): LongArray {
            upsertedEntities = entities
            return entities.map { it.id }.toLongArray()
        }
    }
}
