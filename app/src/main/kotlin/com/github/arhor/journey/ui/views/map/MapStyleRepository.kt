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

class MapStyleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun resolve(style: MapStyle): MapResolvedStyle = when (style) {
        MapStyle.DEFAULT -> MapResolvedStyle.Uri(defaultStyleFallbackUri)
        MapStyle.TERRAIN -> resolveFromAssetOrFallback(TERRAIN_STYLE_ASSET_PATH, TERRAIN_STYLE_URI)
        else -> MapResolvedStyle.Uri(resolveRemoteStyleUri(style))
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
        const val defaultStyleFallbackUri = "https://tiles.openfreemap.org/styles/liberty"

        const val CLASSIC_STYLE_URI = "https://tiles.openfreemap.org/styles/bright"
        const val DARK_STYLE_URI = "https://tiles.openfreemap.org/styles/dark"
        const val SATELLITE_STYLE_URI = "https://tiles.openfreemap.org/styles/positron"
        const val TERRAIN_STYLE_URI = "https://tiles.openfreemap.org/styles/liberty"

        private const val TERRAIN_STYLE_ASSET_PATH = "map/styles/default.json"
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

internal fun resolveRemoteStyleUri(style: MapStyle): String =
    when (style) {
        MapStyle.DEFAULT -> MapStyleRepository.defaultStyleFallbackUri
        MapStyle.CLASSIC -> MapStyleRepository.CLASSIC_STYLE_URI
        MapStyle.DARK -> MapStyleRepository.DARK_STYLE_URI
        MapStyle.SATELLITE -> MapStyleRepository.SATELLITE_STYLE_URI
        MapStyle.TERRAIN -> MapStyleRepository.TERRAIN_STYLE_URI
    }
