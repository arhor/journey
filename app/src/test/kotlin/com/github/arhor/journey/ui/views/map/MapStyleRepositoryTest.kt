package com.github.arhor.journey.ui.views.map

import com.github.arhor.journey.domain.model.MapStyle
import io.kotest.matchers.shouldBe
import org.junit.Test

class MapStyleRepositoryTest {
    @Test
    fun `resolveRemoteStyleUri should return expected uri when each style is selected`() {
        // Given
        val styles = MapStyle.entries

        // When
        val resolved = styles.associateWith(::resolveRemoteStyleUri)

        // Then
        resolved[MapStyle.DEFAULT] shouldBe MapStyleRepository.defaultStyleFallbackUri
        resolved[MapStyle.CLASSIC] shouldBe MapStyleRepository.CLASSIC_STYLE_URI
        resolved[MapStyle.DARK] shouldBe MapStyleRepository.DARK_STYLE_URI
        resolved[MapStyle.SATELLITE] shouldBe MapStyleRepository.SATELLITE_STYLE_URI
        resolved[MapStyle.TERRAIN] shouldBe MapStyleRepository.TERRAIN_STYLE_URI
    }

    @Test
    fun `isRenderableMapStyle should return false when style contains only background layer`() {
        // Given
        val styleJson = """
            {
              "version": 8,
              "sources": {},
              "layers": [
                {
                  "id": "background",
                  "type": "background"
                }
              ]
            }
        """.trimIndent()

        // When
        val result = isRenderableMapStyle(styleJson)

        // Then
        result shouldBe false
    }

    @Test
    fun `isRenderableMapStyle should return true when style contains sources and renderable layers`() {
        // Given
        val styleJson = """
            {
              "version": 8,
              "sources": {
                "openmaptiles": {
                  "type": "vector",
                  "url": "https://example.com/tiles.json"
                }
              },
              "layers": [
                {
                  "id": "water",
                  "type": "fill",
                  "source": "openmaptiles",
                  "source-layer": "water"
                }
              ]
            }
        """.trimIndent()

        // When
        val result = isRenderableMapStyle(styleJson)

        // Then
        result shouldBe true
    }

    @Test
    fun `isRenderableMapStyle should return false when style json is malformed`() {
        // Given
        val styleJson = "{"

        // When
        val result = isRenderableMapStyle(styleJson)

        // Then
        result shouldBe false
    }
}
