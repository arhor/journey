package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.PoiCategory
import com.github.arhor.journey.domain.model.PointOfInterest
import com.github.arhor.journey.domain.repository.PointOfInterestRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObservePointsOfInterestUseCaseTest {

    @Test
    fun `invoke should call ensureSeeded and emit points of interest when collection starts`() = runTest {
        // Given
        val expected = listOf(
            PointOfInterest(
                id = "poi-1",
                name = "Old Oak",
                description = "A large oak tree",
                category = PoiCategory.LANDMARK,
                location = GeoPoint(lat = 50.45, lon = 30.52),
                radiusMeters = 30,
            ),
        )
        val repository = FakePointOfInterestRepository(flow = flowOf(expected))
        val subject = ObservePointsOfInterestUseCase(repository = repository)

        // When
        val actual = subject().first()

        // Then
        actual shouldBe expected
        repository.ensureSeededCalls shouldBe 1
    }

    @Test
    fun `invoke should fail collection when ensureSeeded throws before upstream emits`() = runTest {
        // Given
        val expectedError = IllegalStateException("seeding failed")
        val repository = FakePointOfInterestRepository(
            flow = flow { emit(emptyList()) },
            ensureSeededError = expectedError,
        )
        val subject = ObservePointsOfInterestUseCase(repository = repository)

        // When
        val result = runCatching { subject().first() }

        // Then
        result.exceptionOrNull() shouldBe expectedError
        repository.ensureSeededCalls shouldBe 1
    }

    private class FakePointOfInterestRepository(
        private val flow: Flow<List<PointOfInterest>>,
        private val ensureSeededError: Throwable? = null,
    ) : PointOfInterestRepository {
        var ensureSeededCalls: Int = 0

        override fun observeAll(): Flow<List<PointOfInterest>> = flow

        override suspend fun getById(id: String): PointOfInterest? = null

        override suspend fun upsert(pointOfInterest: PointOfInterest) = Unit

        override suspend fun ensureSeeded() {
            ensureSeededCalls += 1
            ensureSeededError?.let { throw it }
        }
    }
}
