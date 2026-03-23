package com.github.arhor.journey.feature.map.fow

import com.github.arhor.journey.domain.model.ExplorationTileRange
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import org.junit.Test
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Polygon
import java.util.concurrent.CancellationException

class FowRenderDataFactoryTest {

    @Test
    fun `create should return null when fog ranges are empty`() {
        // Given
        val factory = FowRenderDataFactory()

        // When
        val actual = factory.create(emptyList())

        // Then
        actual shouldBe null
    }

    @Test
    fun `create should reuse cached render data for equivalent fog inputs`() {
        // Given
        val factory = FowRenderDataFactory()
        val fogRanges = listOf(
            ExplorationTileRange(
                zoom = 16,
                minX = 34567,
                maxX = 34568,
                minY = 22345,
                maxY = 22345,
            ),
            ExplorationTileRange(
                zoom = 16,
                minX = 34567,
                maxX = 34567,
                minY = 22346,
                maxY = 22346,
            ),
        )

        // When
        val first = factory.create(fogRanges)
        val second = factory.create(fogRanges.asReversed())

        // Then
        first.shouldNotBeNull()
        second.shouldNotBeNull()
        (first === second) shouldBe true
    }

    @Test
    fun `create should stop geometry preparation when cancellation is requested`() {
        // Given
        val factory = FowRenderDataFactory()
        val fogRanges = listOf(
            ExplorationTileRange(
                zoom = 16,
                minX = 34567,
                maxX = 34570,
                minY = 22345,
                maxY = 22348,
            ),
        )
        var cancellationChecks = 0

        // When
        val result = runCatching {
            factory.create(fogRanges) {
                cancellationChecks += 1
                if (cancellationChecks >= 2) {
                    throw CancellationException("cancelled")
                }
            }
        }

        // Then
        (result.exceptionOrNull() is CancellationException) shouldBe true
        (cancellationChecks >= 2) shouldBe true
    }

    @Test
    fun `toFeatureCollection should emit rounded merged polygons for connected fog tiles`() {
        // Given
        val fogRanges = listOf(
            ExplorationTileRange(
                zoom = 16,
                minX = 34567,
                maxX = 34568,
                minY = 22345,
                maxY = 22345,
            ),
            ExplorationTileRange(
                zoom = 16,
                minX = 34567,
                maxX = 34567,
                minY = 22346,
                maxY = 22346,
            ),
        )

        // When
        val actual = fogRanges.toFeatureCollection()

        // Then
        (actual.features.single().geometry.coordinates.single().size > 5) shouldBe true
    }

    internal fun List<ExplorationTileRange>.toFeatureCollection(
        checkCancelled: () -> Unit = {},
    ): FeatureCollection<Polygon, JsonObject?> =
        this.toTileRegionGeometriesBuildResult(checkCancelled = checkCancelled)
            .geometries
            .toPolygonFeatureCollection(checkCancelled = checkCancelled)
}
