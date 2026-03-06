package com.github.arhor.journey.ui.views.map

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
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

        return if (styleJson.isNullOrBlank()) {
            MapResolvedStyle.Uri(MapUiState.DefaultStyleUri)
        } else {
            MapResolvedStyle.Json(styleJson)
        }
    }

    private companion object {
        const val DEFAULT_STYLE_ASSET_PATH = "map/styles/default.json"
    }
}
