package com.github.arhor.journey.feature.map.prewarm

import com.github.arhor.journey.domain.model.MapStyle
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class MapTileStyleResolverTest {

    private val resolver = MapTileStyleResolver(
        json = Json {
            ignoreUnknownKeys = true
        },
    )

    @Test
    fun `resolve should collect remote tile sprite and glyph urls when bundle style contains supported sources`() =
        runTest {
            // Given
            val style = MapStyle.bundle(
                id = "bundle-style",
                name = "Bundle",
                value = """
                    {
                      "version": 8,
                      "sprite": "https://example.com/sprites/base",
                      "glyphs": "https://example.com/fonts/{fontstack}/{range}.pbf",
                      "sources": {
                        "remoteVector": {
                          "type": "vector",
                          "tiles": ["https://tiles.example.com/{z}/{x}/{y}{ratio}.mvt"],
                          "minzoom": 3,
                          "maxzoom": 8
                        },
                        "localRaster": {
                          "type": "raster",
                          "tiles": ["asset://tiles/{z}/{x}/{y}.png"]
                        }
                      },
                      "layers": [
                        {
                          "id": "labels",
                          "type": "symbol",
                          "layout": {
                            "text-font": ["Noto Sans Regular"]
                          }
                        }
                      ]
                    }
                """.trimIndent(),
            )

            // When
            val actual = resolver.resolve(
                style = style,
                fetcher = FakeMapTileResourceFetcher(),
                pixelRatio = 2f,
            )

            // Then
            actual.metadataResources shouldBe emptyList()
            actual.tileSources shouldHaveSize 1
            actual.tileSources.single().tileTemplates shouldBe listOf(
                "https://tiles.example.com/{z}/{x}/{y}{ratio}.mvt",
            )
            actual.tileSources.single().minZoom shouldBe 3
            actual.tileSources.single().maxZoom shouldBe 8
            actual.staticResourceUrls.toSet() shouldBe setOf(
                "https://example.com/sprites/base@2x.json",
                "https://example.com/sprites/base@2x.png",
                "https://example.com/fonts/Noto%20Sans%20Regular/0-255.pbf",
                "https://example.com/fonts/Noto%20Sans%20Regular/256-511.pbf",
            )
        }

    @Test
    fun `resolve should fetch style and tilejson metadata when remote style uses source urls`() = runTest {
        // Given
        val styleUrl = "https://example.com/maps/style.json"
        val tileJsonUrl = "https://example.com/maps/tileset.json"
        val style = MapStyle.remote(
            id = "remote-style",
            name = "Remote",
            value = styleUrl,
        )
        val fetcher = FakeMapTileResourceFetcher(
            responses = mapOf(
                styleUrl to fetchedResource(
                    url = styleUrl,
                    body = """
                        {
                          "version": 8,
                          "sprite": "./sprites/base",
                          "glyphs": "./fonts/{fontstack}/{range}.pbf",
                          "sources": {
                            "main": {
                              "type": "vector",
                              "url": "./tileset.json"
                            }
                          },
                          "layers": [
                            {
                              "id": "labels",
                              "type": "symbol",
                              "layout": {
                                "text-font": ["literal", ["Inter Regular"]]
                              }
                            }
                          ]
                        }
                    """.trimIndent(),
                ),
                tileJsonUrl to fetchedResource(
                    url = tileJsonUrl,
                    body = """
                        {
                          "tiles": ["./tiles/{z}/{x}/{y}.mvt"],
                          "minzoom": 2,
                          "maxzoom": 6,
                          "bounds": [10.0, 20.0, 30.0, 40.0],
                          "scheme": "tms"
                        }
                    """.trimIndent(),
                ),
            ),
        )

        // When
        val actual = resolver.resolve(
            style = style,
            fetcher = fetcher,
            pixelRatio = 1f,
        )

        // Then
        actual.metadataResources.map(FetchedMapResource::url) shouldBe listOf(
            styleUrl,
            tileJsonUrl,
        )
        actual.tileSources.single().tileTemplates shouldBe listOf(
            "https://example.com/maps/tiles/{z}/{x}/{y}.mvt",
        )
        actual.tileSources.single().scheme shouldBe TileScheme.TMS
        actual.staticResourceUrls shouldContain "https://example.com/maps/sprites/base.json"
        actual.staticResourceUrls shouldContain "https://example.com/maps/fonts/Inter%20Regular/0-255.pbf"
    }

    private fun fetchedResource(
        url: String,
        body: String,
    ): FetchedMapResource = FetchedMapResource(
        url = url,
        body = body.encodeToByteArray(),
    )
}

private class FakeMapTileResourceFetcher(
    private val responses: Map<String, FetchedMapResource> = emptyMap(),
) : MapTileResourceFetcher {
    override suspend fun fetch(url: String): FetchedMapResource? = responses[url]
}
