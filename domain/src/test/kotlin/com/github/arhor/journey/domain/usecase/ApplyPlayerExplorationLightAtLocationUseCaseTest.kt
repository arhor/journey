package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileLight
import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfigHolder
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.repository.ExplorationTileRepository
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ApplyPlayerExplorationLightAtLocationUseCaseTest {

    @Test
    fun `invoke should generate player light ring contributions and persist them`() = runTest {
        // Given
        val repository = FakeExplorationTileRepository()
        val configHolder = ExplorationTileRuntimeConfigHolder().apply {
            setCanonicalZoom(3)
        }
        val subject = ApplyPlayerExplorationLightAtLocationUseCase(
            repository = repository,
            configHolder = configHolder,
        )

        // When
        val actual = subject(
            location = GeoPoint(
                lat = 0.0,
                lon = 0.0,
            ),
        )

        // Then
        actual.single { it.tile == ExplorationTile(zoom = 3, x = 4, y = 4) }.light shouldBe 1.0f
        actual.single { it.tile == ExplorationTile(zoom = 3, x = 3, y = 4) }.light shouldBe 0.66f
        actual.single { it.tile == ExplorationTile(zoom = 3, x = 6, y = 4) }.light shouldBe 0.33f
        repository.accumulatedTileLights shouldContainExactlyInAnyOrder actual.toList()
    }

    private class FakeExplorationTileRepository : ExplorationTileRepository {
        val accumulatedTileLights = mutableListOf<ExplorationTileLight>()

        override fun observeExplorationTileLights(range: ExplorationTileRange): Flow<List<ExplorationTileLight>> = emptyFlow()

        override suspend fun accumulateExplorationTileLights(tileLights: Collection<ExplorationTileLight>) {
            accumulatedTileLights += tileLights
        }

        override suspend fun clearExplorationTileLights() = Unit
    }
}
