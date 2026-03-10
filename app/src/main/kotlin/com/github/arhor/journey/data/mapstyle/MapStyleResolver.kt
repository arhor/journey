package com.github.arhor.journey.data.mapstyle

import android.content.Context
import com.github.arhor.journey.domain.model.MapResolvedStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Singleton

@Singleton
class MapStyleResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    fun resolve(style: MapStyleRecord): MapResolvedStyle {
        return when (style.source) {
            MapStyleRecord.Source.REMOTE -> MapResolvedStyle.Uri(style.uri.orEmpty())
            MapStyleRecord.Source.BUNDLE -> resolveFromBundleOrFallback(style)
        }
    }

    private fun resolveFromBundleOrFallback(style: MapStyleRecord): MapResolvedStyle {
        val styleJson = style.rawStyleJson ?: readFromAsset(style.assetPath)
        val fallbackUri = style.fallbackUri.orEmpty()

        return if (styleJson.isNullOrBlank() || !isRenderableMapStyle(styleJson)) {
            MapResolvedStyle.Uri(fallbackUri)
        } else {
            MapResolvedStyle.Json(styleJson)
        }
    }

    private fun readFromAsset(assetPath: String?): String? {
        if (assetPath.isNullOrBlank()) return null

        return runCatching {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    private fun isRenderableMapStyle(styleJson: String): Boolean {
        val root = runCatching { json.parseToJsonElement(styleJson).jsonObject }.getOrNull() ?: return false

        val hasSources = root["sources"]?.jsonObject?.isNotEmpty() == true
        val hasRenderableLayers = root["layers"]?.jsonArray?.any { layer ->
            layer.jsonObject["type"]?.jsonPrimitive?.contentOrNull != "background"
        } == true

        return hasSources && hasRenderableLayers
    }
}
