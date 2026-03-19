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

class AddPointOfInterestUseCaseTest {

    @Test
    fun `invoke should persist point of interest with generated id`() = runTest {
        // Given
        val repository = FakePointOfInterestRepository()
        val subject = AddPointOfInterestUseCase(repository = repository)

        // When
        val id = subject(
            name = "Custom point",
            description = "User created",
            category = PoiCategory.RESOURCE_NODE,
            location = GeoPoint(lat = 10.0, lon = 20.0),
            radiusMeters = 75,
        )

        // Then
        repository.savedPointOfInterest shouldBe PointOfInterest(
            id = id,
            name = "Custom point",
            description = "User created",
            category = PoiCategory.RESOURCE_NODE,
            location = GeoPoint(lat = 10.0, lon = 20.0),
            radiusMeters = 75,
        )
    }

    private class FakePointOfInterestRepository : PointOfInterestRepository {
        var savedPointOfInterest: PointOfInterest? = null

        override fun observeAll(): Flow<List<PointOfInterest>> = emptyFlow()

        override suspend fun getById(id: Long): PointOfInterest? = null

        override suspend fun upsert(pointOfInterest: PointOfInterest): Long {
            savedPointOfInterest = pointOfInterest
            return pointOfInterest.id
        }
    }
}
