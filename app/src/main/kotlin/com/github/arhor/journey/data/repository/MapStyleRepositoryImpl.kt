package com.github.arhor.journey.data.repository

import android.content.Context
import com.github.arhor.journey.domain.model.MapResolvedStyle
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.MapStyleDefinition
import com.github.arhor.journey.domain.repository.MapStyleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Singleton

@Singleton
class MapStyleRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : MapStyleRepository {
    override fun resolve(style: MapStyle): MapResolvedStyle {
        return when (val definition = resolveMapStyleDefinition(style)) {
            is MapStyleDefinition.Remote -> MapResolvedStyle.Uri(definition.uri)
            is MapStyleDefinition.Asset -> resolveFromAssetOrFallback(definition.path, definition.fallbackUri)
        }
    }

    internal fun resolveMapStyleDefinition(style: MapStyle): MapStyleDefinition =
        when (style) {
            MapStyle.DEFAULT -> {
                MapStyleDefinition.Asset(
                    path = DEFAULT_STYLE_ASSET_PATH,
                    fallbackUri = DEFAULT_STYLE_FALLBACK_URI,
                )
            }

            MapStyle.CLASSIC -> {
                MapStyleDefinition.Remote(uri = CLASSIC_STYLE_URI)
            }

            MapStyle.DARK -> {
                MapStyleDefinition.Remote(uri = DARK_STYLE_URI)
            }

            MapStyle.SATELLITE -> {
                MapStyleDefinition.Asset(
                    path = SATELLITE_STYLE_ASSET_PATH,
                    fallbackUri = DEFAULT_STYLE_FALLBACK_URI,
                )
            }

            MapStyle.TERRAIN -> {
                MapStyleDefinition.Asset(
                    path = TERRAIN_STYLE_ASSET_PATH,
                    fallbackUri = DEFAULT_STYLE_FALLBACK_URI,
                )
            }
        }

    private fun resolveFromAssetOrFallback(assetPath: String, fallbackUri: String): MapResolvedStyle {
        val styleJson = runCatching {
            context.assets.open(assetPath)
                .bufferedReader()
                .use { it.readText() }
        }.getOrNull()

        return if (styleJson.isNullOrBlank() || !isRenderableMapStyle(styleJson)) {
            MapResolvedStyle.Uri(fallbackUri)
        } else {
            MapResolvedStyle.Json(styleJson)
        }
    }

    internal fun isRenderableMapStyle(styleJson: String): Boolean {
        val root = runCatching { json.parseToJsonElement(styleJson).jsonObject }.getOrNull() ?: return false

        val hasSources = root["sources"]
            ?.jsonObject
            ?.isNotEmpty() == true

        val hasRenderableLayers = root["layers"]
            ?.jsonArray
            ?.any { layer ->
                layer.jsonObject["type"]
                    ?.jsonPrimitive
                    ?.contentOrNull != "background"
            } == true

        return hasSources && hasRenderableLayers
    }

    companion object {
        const val DEFAULT_STYLE_FALLBACK_URI = "https://tiles.openfreemap.org/styles/liberty"
        const val CLASSIC_STYLE_URI = "https://tiles.openfreemap.org/styles/bright"
        const val DARK_STYLE_URI = "https://tiles.openfreemap.org/styles/dark"

        const val DEFAULT_STYLE_ASSET_PATH = "map/styles/default.json"
        const val SATELLITE_STYLE_ASSET_PATH = "map/styles/satellite.json"
        const val TERRAIN_STYLE_ASSET_PATH = "map/styles/terrain.json"
    }
}
