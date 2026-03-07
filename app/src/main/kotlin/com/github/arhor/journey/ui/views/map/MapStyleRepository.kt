package com.github.arhor.journey.ui.views.map

import android.content.Context
import com.github.arhor.journey.domain.model.MapStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

sealed interface MapResolvedStyle {
    data class Uri(val value: String) : MapResolvedStyle
    data class Json(val value: String) : MapResolvedStyle
}

internal sealed interface MapStyleDefinition {
    data class Remote(val uri: String) : MapStyleDefinition

    data class Asset(
        val path: String,
        val fallbackUri: String,
    ) : MapStyleDefinition
}

internal fun resolveMapStyleDefinition(style: MapStyle): MapStyleDefinition =
    when (style) {
        MapStyle.DEFAULT -> MapStyleDefinition.Asset(
            path = MapStyleRepository.DEFAULT_STYLE_ASSET_PATH,
            fallbackUri = MapStyleRepository.DEFAULT_STYLE_FALLBACK_URI,
        )
        MapStyle.CLASSIC -> MapStyleDefinition.Remote(uri = MapStyleRepository.CLASSIC_STYLE_URI)
        MapStyle.DARK -> MapStyleDefinition.Remote(uri = MapStyleRepository.DARK_STYLE_URI)
        MapStyle.SATELLITE -> MapStyleDefinition.Asset(
            path = MapStyleRepository.SATELLITE_STYLE_ASSET_PATH,
            fallbackUri = MapStyleRepository.DEFAULT_STYLE_FALLBACK_URI,
        )
        MapStyle.TERRAIN -> MapStyleDefinition.Asset(
            path = MapStyleRepository.TERRAIN_STYLE_ASSET_PATH,
            fallbackUri = MapStyleRepository.DEFAULT_STYLE_FALLBACK_URI,
        )
    }

class MapStyleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun resolve(style: MapStyle): MapResolvedStyle {
        return when (val definition = resolveMapStyleDefinition(style)) {
            is MapStyleDefinition.Remote -> MapResolvedStyle.Uri(definition.uri)
            is MapStyleDefinition.Asset -> resolveFromAssetOrFallback(definition.path, definition.fallbackUri)
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

    companion object {
        const val DEFAULT_STYLE_FALLBACK_URI = "https://tiles.openfreemap.org/styles/liberty"
        const val CLASSIC_STYLE_URI = "https://tiles.openfreemap.org/styles/bright"
        const val DARK_STYLE_URI = "https://tiles.openfreemap.org/styles/dark"

        const val DEFAULT_STYLE_ASSET_PATH = "map/styles/default.json"
        const val SATELLITE_STYLE_ASSET_PATH = "map/styles/satellite.json"
        const val TERRAIN_STYLE_ASSET_PATH = "map/styles/terrain.json"
    }
}

internal fun isRenderableMapStyle(styleJson: String): Boolean {
    val root = runCatching { Json.parseToJsonElement(styleJson).jsonObject }.getOrNull() ?: return false

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
