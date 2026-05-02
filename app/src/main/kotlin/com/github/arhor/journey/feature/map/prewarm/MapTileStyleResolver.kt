package com.github.arhor.journey.feature.map.prewarm

import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.MapStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URL
import javax.inject.Inject

class MapTileStyleResolver @Inject constructor(
    private val json: Json,
) {
    internal suspend fun resolve(
        style: MapStyle,
        fetcher: MapTileResourceFetcher,
        pixelRatio: Float,
    ): ResolvedMapStyleResources {
        val metadataResources = mutableListOf<FetchedMapResource>()
        val styleBaseUrl: String?
        val styleDocument = when (style.type) {
            MapStyle.Type.BUNDLE -> {
                styleBaseUrl = null
                parseJsonObject(style.value) ?: return ResolvedMapStyleResources()
            }

            MapStyle.Type.REMOTE -> {
                val resolvedStyleUrl = style.value.takeIf(::isRemoteHttpUrl)
                    ?: return ResolvedMapStyleResources()
                val styleResource = fetcher.fetch(resolvedStyleUrl)
                    ?: return ResolvedMapStyleResources()
                metadataResources += styleResource
                styleBaseUrl = styleResource.url
                parseJsonObject(styleResource.body.decodeToString())
                    ?: return ResolvedMapStyleResources(metadataResources = metadataResources)
            }
        }

        val staticResourceUrls = buildList {
            addAll(resolveSpriteUrls(styleDocument, styleBaseUrl, pixelRatio))
            addAll(resolveGlyphUrls(styleDocument, styleBaseUrl))
        }

        val tileSources = mutableListOf<TileSourceDefinition>()
        styleDocument.jsonObjectOrNull("sources")
            ?.values
            ?.forEach { sourceElement ->
                val sourceObject = sourceElement as? JsonObject ?: return@forEach
                val tileSource = resolveTileSource(
                    source = sourceObject,
                    styleBaseUrl = styleBaseUrl,
                    fetcher = fetcher,
                    metadataResources = metadataResources,
                )
                if (tileSource != null) {
                    tileSources += tileSource
                }
            }

        return ResolvedMapStyleResources(
            metadataResources = metadataResources.distinctBy(FetchedMapResource::url),
            staticResourceUrls = staticResourceUrls.distinct(),
            tileSources = tileSources,
        )
    }

    private suspend fun resolveTileSource(
        source: JsonObject,
        styleBaseUrl: String?,
        fetcher: MapTileResourceFetcher,
        metadataResources: MutableList<FetchedMapResource>,
    ): TileSourceDefinition? {
        val sourceType = source.stringOrNull("type")
        if (sourceType !in SUPPORTED_TILE_SOURCE_TYPES) {
            return null
        }

        val directTiles = source.stringArray("tiles")
            ?.mapNotNull { resolveUrl(styleBaseUrl, it) }
            ?.filter(::isRemoteHttpUrl)
            .orEmpty()

        if (directTiles.isNotEmpty()) {
            return TileSourceDefinition(
                tileTemplates = directTiles,
                minZoom = source.intOrNull("minzoom") ?: DEFAULT_MIN_TILE_ZOOM,
                maxZoom = source.intOrNull("maxzoom") ?: DEFAULT_MAX_TILE_ZOOM,
                bounds = source.boundsOrNull("bounds"),
                scheme = TileScheme.fromRawValue(source.stringOrNull("scheme")),
            )
        }

        val sourceUrl = source.stringOrNull("url")
            ?.let { resolveUrl(styleBaseUrl, it) }
            ?.takeIf(::isRemoteHttpUrl)
            ?: return null

        val tileJsonResource = fetcher.fetch(sourceUrl) ?: return null
        metadataResources += tileJsonResource

        val tileJson = parseJsonObject(tileJsonResource.body.decodeToString()) ?: return null
        val tiles = tileJson.stringArray("tiles")
            ?.mapNotNull { resolveUrl(tileJsonResource.url, it) }
            ?.filter(::isRemoteHttpUrl)
            .orEmpty()
        if (tiles.isEmpty()) {
            return null
        }

        return TileSourceDefinition(
            tileTemplates = tiles,
            minZoom = tileJson.intOrNull("minzoom")
                ?: source.intOrNull("minzoom")
                ?: DEFAULT_MIN_TILE_ZOOM,
            maxZoom = tileJson.intOrNull("maxzoom")
                ?: source.intOrNull("maxzoom")
                ?: DEFAULT_MAX_TILE_ZOOM,
            bounds = tileJson.boundsOrNull("bounds") ?: source.boundsOrNull("bounds"),
            scheme = TileScheme.fromRawValue(
                tileJson.stringOrNull("scheme") ?: source.stringOrNull("scheme"),
            ),
        )
    }

    private fun resolveSpriteUrls(
        styleDocument: JsonObject,
        styleBaseUrl: String?,
        pixelRatio: Float,
    ): List<String> {
        val spriteBase = styleDocument.stringOrNull("sprite")
            ?.let { resolveUrl(styleBaseUrl, it) }
            ?.takeIf(::isRemoteHttpUrl)
            ?: return emptyList()
        val spriteScaleSuffix = if (pixelRatio >= RETINA_PIXEL_RATIO_THRESHOLD) "@2x" else ""
        return listOf(
            appendSpriteExtension(spriteBase, "$spriteScaleSuffix.json"),
            appendSpriteExtension(spriteBase, "$spriteScaleSuffix.png"),
        )
    }

    private fun resolveGlyphUrls(
        styleDocument: JsonObject,
        styleBaseUrl: String?,
    ): List<String> {
        val glyphTemplate = styleDocument.stringOrNull("glyphs")
            ?.let { resolveUrl(styleBaseUrl, it) }
            ?.takeIf(::isRemoteHttpUrl)
            ?: return emptyList()

        return extractFontStacks(styleDocument)
            .take(MAX_FONT_STACK_COUNT)
            .flatMap { fontStack ->
                GLYPH_RANGES.map { range ->
                    glyphTemplate
                        .replace("{fontstack}", encodeUrlPathSegment(fontStack))
                        .replace("{range}", range)
                }
            }
    }

    private fun extractFontStacks(styleDocument: JsonObject): List<String> {
        val result = linkedSetOf<String>()

        styleDocument.jsonArrayOrNull("layers")
            ?.forEach { layerElement ->
                val layer = layerElement as? JsonObject ?: return@forEach
                val layout = layer.jsonObjectOrNull("layout") ?: return@forEach
                when (val fontElement = layout["text-font"]) {
                    is JsonArray -> {
                        val fonts = parseFontArray(fontElement)
                        result += fonts
                    }

                    else -> Unit
                }
            }

        return result.toList()
    }

    private fun parseFontArray(fontArray: JsonArray): List<String> {
        val literalValues = fontArray
            .mapNotNull { element -> (element as? JsonPrimitive)?.contentOrNull }
        if (literalValues.isNotEmpty() && literalValues.first() != "literal") {
            return literalValues
        }

        val literalArray = fontArray.getOrNull(1) as? JsonArray ?: return emptyList()
        return literalArray.mapNotNull { element -> (element as? JsonPrimitive)?.contentOrNull }
    }

    private fun parseJsonObject(value: String): JsonObject? = runCatching {
        json.parseToJsonElement(value).jsonObject
    }.getOrNull()

    private fun appendSpriteExtension(baseUrl: String, suffix: String): String {
        val uri = runCatching { URI(baseUrl) }.getOrNull()
            ?: return baseUrl + suffix
        return URI(
            uri.scheme,
            uri.authority,
            "${uri.path.orEmpty()}$suffix",
            uri.query,
            uri.fragment,
        ).toString()
    }

    private fun resolveUrl(baseUrl: String?, rawUrl: String): String? {
        if (SCHEME_REGEX.containsMatchIn(rawUrl)) {
            return rawUrl
        }
        val resolvedBaseUrl = baseUrl ?: return null
        return runCatching {
            URL(URL(resolvedBaseUrl), rawUrl).toString()
        }.getOrNull()
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key]?.jsonPrimitive?.contentOrNull)

    private fun JsonObject.intOrNull(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.jsonArrayOrNull(key: String): JsonArray? =
        this[key] as? JsonArray

    private fun JsonObject.jsonObjectOrNull(key: String): JsonObject? =
        this[key] as? JsonObject

    private fun JsonObject.stringArray(key: String): List<String>? =
        jsonArrayOrNull(key)
            ?.mapNotNull { element -> element.jsonPrimitive.contentOrNull }

    private fun JsonObject.boundsOrNull(key: String): GeoBounds? {
        val values = jsonArrayOrNull(key)
            ?.mapNotNull { element -> element.jsonPrimitive.doubleOrNull }
            ?: return null
        if (values.size != 4) {
            return null
        }
        return GeoBounds(
            south = values[1],
            west = values[0],
            north = values[3],
            east = values[2],
        )
    }

    private companion object {
        private const val DEFAULT_MIN_TILE_ZOOM = 0
        private const val DEFAULT_MAX_TILE_ZOOM = 22
        private const val MAX_FONT_STACK_COUNT = 4
        private const val RETINA_PIXEL_RATIO_THRESHOLD = 1.5f
        private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
        private val GLYPH_RANGES = listOf("0-255", "256-511")
        private val SUPPORTED_TILE_SOURCE_TYPES = setOf("vector", "raster")
    }
}

internal data class ResolvedMapStyleResources(
    val metadataResources: List<FetchedMapResource> = emptyList(),
    val staticResourceUrls: List<String> = emptyList(),
    val tileSources: List<TileSourceDefinition> = emptyList(),
)

internal data class TileSourceDefinition(
    val tileTemplates: List<String>,
    val minZoom: Int,
    val maxZoom: Int,
    val bounds: GeoBounds?,
    val scheme: TileScheme,
)

internal enum class TileScheme {
    XYZ,
    TMS,
    ;

    companion object {
        fun fromRawValue(rawValue: String?): TileScheme =
            if (rawValue.equals("tms", ignoreCase = true)) TMS else XYZ
    }
}
