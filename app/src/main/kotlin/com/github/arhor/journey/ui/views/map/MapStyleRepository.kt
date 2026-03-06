package com.github.arhor.journey.ui.views.map

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

enum class MapStyleKey {
    Default,
}

sealed interface MapResolvedStyle {
    data class Uri(val value: String) : MapResolvedStyle
    data class Json(val value: String) : MapResolvedStyle
}

class MapStyleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun resolve(styleKey: MapStyleKey): MapResolvedStyle = when (styleKey) {
        MapStyleKey.Default -> resolveFromAssetOrFallback(DEFAULT_STYLE_ASSET_PATH)
    }

    private fun resolveFromAssetOrFallback(assetPath: String): MapResolvedStyle {
        val styleJson = runCatching {
            context.assets.open(assetPath)
                .bufferedReader()
                .use { it.readText() }
        }.getOrNull()

        return if (styleJson.isNullOrBlank() || !isRenderableMapStyle(styleJson)) {
            MapResolvedStyle.Uri(MapUiState.DefaultStyleUri)
        } else {
            MapResolvedStyle.Json(styleJson)
        }
    }

    private companion object {
        const val DEFAULT_STYLE_ASSET_PATH = "map/styles/default.json"
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
