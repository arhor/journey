package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.PoiCategory
import com.github.arhor.journey.domain.model.PointOfInterest
import com.github.arhor.journey.domain.repository.PointOfInterestRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetPointOfInterestUseCaseTest {

    @Test
    fun `invoke should return point of interest from repository when id exists`() = runTest {
        // Given
        val expected = PointOfInterest(
            id = "poi-1",
            name = "Old Town Market Square",
            description = "Historic square",
            category = PoiCategory.LANDMARK,
            location = GeoPoint(lat = 52.2497, lon = 21.0122),
            radiusMeters = 60,
        )
        val repository = FakePointOfInterestRepository(pointOfInterest = expected)
        val subject = GetPointOfInterestUseCase(repository = repository)

        // When
        val actual = subject("poi-1")

        // Then
        actual shouldBe expected
        repository.requestedId shouldBe "poi-1"
    }

    private class FakePointOfInterestRepository(
        private val pointOfInterest: PointOfInterest?,
    ) : PointOfInterestRepository {
        var requestedId: String? = null

        override fun observeAll(): Flow<List<PointOfInterest>> = emptyFlow()

        override suspend fun getById(id: String): PointOfInterest? {
            requestedId = id
            return pointOfInterest
        }

        override suspend fun ensureSeeded() = Unit
    }
}
