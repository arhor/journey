package com.github.arhor.journey.feature.map.prewarm

import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapViewportSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds
import org.junit.Test

class MapTileCoverageCalculatorTest {

    private val calculator = MapTileCoverageCalculator()

    @Test
    fun `calculate should return final viewport tiles when request is an instant jump`() {
        // Given
        val request = MapTilePrewarmRequest(
            requestKey = "map",
            style = MapStyle.remote("style", "Style", "https://example.com/style.json"),
            currentCamera = CameraPositionState(
                target = LatLng(latitude = 0.0, longitude = 0.0),
                zoom = 2.0,
            ),
            targetCamera = CameraPositionState(
                target = LatLng(latitude = 0.0, longitude = 0.0),
                zoom = 2.0,
            ),
            currentVisibleBounds = GeoBounds(
                south = -10.0,
                west = -10.0,
                north = 10.0,
                east = 10.0,
            ),
            viewportSize = MapViewportSize(widthPx = 1080, heightPx = 1920),
            animationDuration = kotlin.time.Duration.ZERO,
            sampleCount = 1,
            burstLimit = 8,
        )
        val source = TileSourceDefinition(
            tileTemplates = listOf("https://tiles.example.com/{z}/{x}/{y}{ratio}.mvt"),
            minZoom = 2,
            maxZoom = 2,
            bounds = null,
            scheme = TileScheme.XYZ,
        )

        // When
        val actual = calculator.calculate(
            request = request,
            sources = listOf(source),
            pixelRatio = 2f,
        )

        // Then
        actual.toSet() shouldBe setOf(
            "https://tiles.example.com/2/1/1@2x.mvt",
            "https://tiles.example.com/2/2/1@2x.mvt",
            "https://tiles.example.com/2/1/2@2x.mvt",
            "https://tiles.example.com/2/2/2@2x.mvt",
        )
    }

    @Test
    fun `calculate should include intermediate zoom levels when request samples an animated move`() {
        // Given
        val request = MapTilePrewarmRequest(
            requestKey = "map",
            style = MapStyle.remote("style", "Style", "https://example.com/style.json"),
            currentCamera = CameraPositionState(
                target = LatLng(latitude = 0.0, longitude = 0.0),
                zoom = 2.0,
            ),
            targetCamera = CameraPositionState(
                target = LatLng(latitude = 30.0, longitude = 30.0),
                zoom = 4.0,
            ),
            currentVisibleBounds = GeoBounds(
                south = -20.0,
                west = -20.0,
                north = 20.0,
                east = 20.0,
            ),
            viewportSize = MapViewportSize(widthPx = 1080, heightPx = 1920),
            animationDuration = 1.seconds,
            sampleCount = 4,
            burstLimit = 32,
        )
        val source = TileSourceDefinition(
            tileTemplates = listOf("https://tiles.example.com/{z}/{x}/{y}.mvt"),
            minZoom = 2,
            maxZoom = 4,
            bounds = null,
            scheme = TileScheme.XYZ,
        )

        // When
        val actual = calculator.calculate(
            request = request,
            sources = listOf(source),
            pixelRatio = 1f,
        )

        // Then
        actual.shouldHaveSize(12)
        actual.any { url -> "/4/" in url } shouldBe true
        actual.any { url -> "/3/" in url } shouldBe true
        actual.any { url -> "/2/" in url } shouldBe true
    }
}
