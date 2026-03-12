package com.github.arhor.journey.data.repository

import android.content.Context
import com.github.arhor.journey.data.mapstyle.BundledMapStyleDataSource
import com.github.arhor.journey.data.mapstyle.MapStyleRecord
import com.github.arhor.journey.data.mapstyle.RemoteMapStyleLocalDataSource
import com.github.arhor.journey.domain.map.model.MapStyle
import com.github.arhor.journey.domain.map.model.ResolvedMapStyle
import com.github.arhor.journey.domain.map.repository.MapStylesRepository
import com.github.arhor.journey.domain.settings.repository.SettingsRepository
import com.github.arhor.journey.domain.settings.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Singleton

@Singleton
class MapStylesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val bundledDataSource: BundledMapStyleDataSource,
    private val remoteLocalDataSource: RemoteMapStyleLocalDataSource,
    private val settingsRepository: SettingsRepository,
) : MapStylesRepository {

    override fun observeAvailableStyles(): Flow<List<MapStyle>> =
        observeMergedStyles().map { styles ->
            styles.map { style ->
                MapStyle(id = style.id, name = style.name)
            }
        }

    override fun observeSelectedStyle(): Flow<ResolvedMapStyle> =
        combine(observeMergedStyles(), settingsRepository.observeSettings()) { styles, settings ->
            val selected = styles.resolveSelectedStyle(settings)
            resolve(selected)
        }

    private fun observeMergedStyles(): Flow<List<MapStyleRecord>> {
        return remoteLocalDataSource.observeCachedStyles().map { cachedRemoteStyles ->
            val bundledStyles = bundledDataSource.getStyles()
            val bundledIds = bundledStyles.map { it.id }.toSet()
            val distinctRemoteStyles = cachedRemoteStyles.filterNot { it.id in bundledIds }
            bundledStyles + distinctRemoteStyles
        }
    }

    private fun List<MapStyleRecord>.resolveSelectedStyle(settings: AppSettings): MapStyleRecord {
        val selectedId = settings.selectedMapStyleId
        return firstOrNull { it.id == selectedId } ?: first { it.id == MapStyle.DEFAULT_ID }
    }

    private fun resolve(style: MapStyleRecord): ResolvedMapStyle {
        return when (style.source) {
            MapStyleRecord.Source.REMOTE -> ResolvedMapStyle.Uri(style.uri.orEmpty())
            MapStyleRecord.Source.BUNDLE -> resolveFromBundleOrFallback(style)
        }
    }

    private fun resolveFromBundleOrFallback(style: MapStyleRecord): ResolvedMapStyle {
        val styleJson = style.rawStyleJson ?: readFromAsset(style.assetPath)
        val fallbackUri = style.fallbackUri.orEmpty()

        return if (styleJson.isNullOrBlank() || !isRenderableMapStyle(styleJson)) {
            ResolvedMapStyle.Uri(fallbackUri)
        } else {
            ResolvedMapStyle.Json(styleJson)
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
