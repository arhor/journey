package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.PoiDao
import com.github.arhor.journey.data.local.db.entity.PoiEntity
import com.github.arhor.journey.data.local.seed.PointOfInterestSeed
import com.github.arhor.journey.data.mapper.toEntity
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomPointOfInterestRepositoryTest {

    @Test
    fun `ensureSeeded should insert seed points when database is empty`() = runTest {
        // Given
        val dao = FakePoiDao(
            countValue = 0,
            observedItems = emptyList(),
        )
        val subject = RoomPointOfInterestRepository(dao = dao)

        // When
        subject.ensureSeeded()

        // Then
        dao.upsertedEntities shouldBe PointOfInterestSeed.items.map { it.toEntity() }
    }

    @Test
    fun `ensureSeeded should skip inserting seed points when database already has records`() = runTest {
        // Given
        val dao = FakePoiDao(
            countValue = 3,
            observedItems = emptyList(),
        )
        val subject = RoomPointOfInterestRepository(dao = dao)

        // When
        subject.ensureSeeded()

        // Then
        dao.upsertedEntities.shouldBeNull()
    }

    @Test
    fun `observeAll should map entities and fallback category when dao emits unknown value`() = runTest {
        // Given
        val dao = FakePoiDao(
            countValue = 1,
            observedItems = listOf(
                PoiEntity(
                    id = "poi-1",
                    name = "Known",
                    description = null,
                    category = "LANDMARK",
                    lat = 52.0,
                    lon = 21.0,
                    radiusMeters = 80,
                ),
                PoiEntity(
                    id = "poi-2",
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
        actual.first().id shouldBe "poi-1"
        actual.last().category.name shouldBe "LANDMARK"
    }

    @Test
    fun `getById should map dao entity when point of interest exists`() = runTest {
        // Given
        val dao = FakePoiDao(
            countValue = 1,
            observedItems = listOf(
                PoiEntity(
                    id = "poi-1",
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
        val actual = subject.getById("poi-1")

        // Then
        actual?.id shouldBe "poi-1"
        actual?.name shouldBe "Known"
    }

    @Test
    fun `upsert should map point of interest and pass entity to dao`() = runTest {
        // Given
        val dao = FakePoiDao(
            countValue = 1,
            observedItems = emptyList(),
        )
        val subject = RoomPointOfInterestRepository(dao = dao)
        val pointOfInterest = PointOfInterestSeed.items.first()

        // When
        subject.upsert(pointOfInterest)

        // Then
        dao.upsertedEntity shouldBe pointOfInterest.toEntity()
    }

    private suspend fun <T> Flow<T>.firstValue(): T = first()

    private class FakePoiDao(
        private val countValue: Int,
        private val observedItems: List<PoiEntity>,
    ) : PoiDao {
        var upsertedEntities: List<PoiEntity>? = null
        var upsertedEntity: PoiEntity? = null

        override fun observeAll(): Flow<List<PoiEntity>> = flowOf(observedItems)

        override suspend fun getById(id: String): PoiEntity? =
            observedItems.firstOrNull { it.id == id }

        override suspend fun count(): Int = countValue

        override suspend fun upsert(entity: PoiEntity) {
            upsertedEntity = entity
        }

        override suspend fun upsertAll(entities: List<PoiEntity>) {
            upsertedEntities = entities
        }
    }
}
