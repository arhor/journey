package com.github.arhor.journey.data.mapstyle

import android.content.Context
import android.content.res.AssetManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.ByteArrayInputStream

class BundledMapStyleDataSourceTest {

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    @Test
    fun `getStyles should load bundled definitions from individual style files when assets contain multiple styles`() {
        // Given
        val assetManager = mockk<AssetManager>()
        val context = mockk<Context>()
        every { context.assets } returns assetManager
        every { assetManager.list("map/styles") } returns arrayOf("default.json", "terrain.json")
        every { assetManager.open("map/styles/default.json") } returns ByteArrayInputStream(defaultStyleJson.toByteArray())
        every { assetManager.open("map/styles/terrain.json") } returns ByteArrayInputStream(terrainStyleJson.toByteArray())

        val dataSource = BundledMapStyleDataSource(context = context, json = json)

        // When
        val styles = dataSource.getStyles()

        // Then
        styles.map { it.id to it.assetPath } shouldBe listOf(
            "default" to "map/styles/default.json",
            "terrain" to "map/styles/terrain.json",
        )
        styles.all { it.source == MapStyleRecord.Source.BUNDLE } shouldBe true
    }

    @Test
    fun `getStyles should load bundled definitions from manifest array when styles are declared in single file`() {
        // Given
        val assetManager = mockk<AssetManager>()
        val context = mockk<Context>()
        every { context.assets } returns assetManager
        every { assetManager.list("map/styles") } returns arrayOf("manifest.json")
        every { assetManager.open("map/styles/manifest.json") } returns ByteArrayInputStream(manifestJson.toByteArray())

        val dataSource = BundledMapStyleDataSource(context = context, json = json)

        // When
        val styles = dataSource.getStyles()

        // Then
        styles.map { it.id to it.name } shouldBe listOf(
            "default" to "Default",
            "dark" to "Dark",
        )
        styles.all { !it.rawStyleJson.isNullOrBlank() } shouldBe true
        styles.all { it.assetPath == null } shouldBe true
    }

    private val defaultStyleJson = """
        {
          "version": 8,
          "sources": {"openmaptiles": {"type": "vector", "url": "https://example.com/tiles.json"}},
          "layers": [{"id": "water", "type": "fill", "source": "openmaptiles", "source-layer": "water"}]
        }
    """.trimIndent()

    private val terrainStyleJson = """
        {
          "version": 8,
          "sources": {"openmaptiles": {"type": "vector", "url": "https://example.com/tiles.json"}},
          "layers": [{"id": "land", "type": "line", "source": "openmaptiles", "source-layer": "land"}]
        }
    """.trimIndent()

    private val manifestJson = """
        {
          "styles": [
            {
              "id": "default",
              "name": "Default",
              "style": {
                "version": 8,
                "sources": {"openmaptiles": {"type": "vector", "url": "https://example.com/tiles.json"}},
                "layers": [{"id": "water", "type": "fill", "source": "openmaptiles", "source-layer": "water"}]
              }
            },
            {
              "id": "dark",
              "name": "Dark",
              "style": {
                "version": 8,
                "sources": {"openmaptiles": {"type": "vector", "url": "https://example.com/tiles.json"}},
                "layers": [{"id": "road", "type": "line", "source": "openmaptiles", "source-layer": "road"}]
              }
            }
          ]
        }
    """.trimIndent()
}
