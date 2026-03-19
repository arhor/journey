package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.DiscoveredPoi
import com.github.arhor.journey.domain.model.ExplorationProgress
import com.github.arhor.journey.domain.repository.ExplorationRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DiscoverPointOfInterestUseCaseTest {

    @Test
    fun `invoke should pass poi id and clock instant to repository`() = runTest {
        // Given
        val now = Instant.parse("2026-03-05T09:30:00Z")
        val poiId = 1L
        val repository = FakeExplorationRepository()
        val subject = DiscoverPointOfInterestUseCase(
            repository = repository,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        // When
        subject(poiId)

        // Then
        repository.discoveries shouldBe listOf(DiscoveredPoi(poiId = poiId, discoveredAt = now))
    }

    @Test
    fun `invoke should propagate repository failure when discover operation throws`() = runTest {
        // Given
        val expectedError = IllegalStateException("db write failed")
        val poiId = 2L
        val repository = FakeExplorationRepository(error = expectedError)
        val subject = DiscoverPointOfInterestUseCase(
            repository = repository,
            clock = Clock.fixed(Instant.parse("2026-03-05T09:30:00Z"), ZoneOffset.UTC),
        )

        // When
        val result = runCatching { subject(poiId) }

        // Then
        result.exceptionOrNull() shouldBe expectedError
    }

    private class FakeExplorationRepository(
        private val error: Throwable? = null,
    ) : ExplorationRepository {
        val discoveries: MutableList<DiscoveredPoi> = mutableListOf()

        override fun observeProgress(): Flow<ExplorationProgress> = emptyFlow()

        override suspend fun discoverPoi(poiId: Long, discoveredAt: Instant) {
            error?.let { throw it }
            discoveries += DiscoveredPoi(poiId = poiId, discoveredAt = discoveredAt)
        }
    }
}
