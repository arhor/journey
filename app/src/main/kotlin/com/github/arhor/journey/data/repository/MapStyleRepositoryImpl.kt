package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.mapstyle.BundledMapStyleDataSource
import com.github.arhor.journey.data.mapstyle.MapStyleRecord
import com.github.arhor.journey.data.mapstyle.MapStyleResolver
import com.github.arhor.journey.data.mapstyle.MapStyleSelectionLocalDataSource
import com.github.arhor.journey.data.mapstyle.RemoteMapStyleLocalDataSource
import com.github.arhor.journey.data.mapstyle.RemoteMapStyleRemoteDataSource
import com.github.arhor.journey.domain.model.MapResolvedStyle
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.repository.MapStyleRepository
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Singleton

@Singleton
class MapStyleRepositoryImpl @Inject constructor(
    private val bundledDataSource: BundledMapStyleDataSource,
    private val remoteLocalDataSource: RemoteMapStyleLocalDataSource,
    private val remoteRemoteDataSource: RemoteMapStyleRemoteDataSource,
    private val selectionLocalDataSource: MapStyleSelectionLocalDataSource,
    private val resolver: MapStyleResolver,
) : MapStyleRepository {

    override fun observeAvailableStyles(): Flow<List<MapStyle>> =
        observeMergedStyles().map { styles ->
            styles.map { style ->
                MapStyle(id = style.id, name = style.name)
            }
        }

    override fun observeSelectedStyle(): Flow<MapStyle> =
        combine(observeAvailableStyles(), selectionLocalDataSource.observeSelectedStyleId(MapStyle.DEFAULT_ID)) {
                styles,
                selectedId,
            ->
            styles.firstOrNull { it.id == selectedId } ?: styles.first { it.id == MapStyle.DEFAULT_ID }
        }

    override fun observeSelectedResolvedStyle(): Flow<MapResolvedStyle> =
        combine(observeMergedStyles(), selectionLocalDataSource.observeSelectedStyleId(MapStyle.DEFAULT_ID)) {
                styles,
                selectedId,
            ->
            val selected = styles.firstOrNull { it.id == selectedId } ?: styles.first { it.id == MapStyle.DEFAULT_ID }
            resolver.resolve(selected)
        }

    override suspend fun selectStyle(styleId: String) {
        val styleIds = observeAvailableStyles().first().map { it.id }.toSet()
        val validStyleId = if (styleId in styleIds) styleId else MapStyle.DEFAULT_ID
        selectionLocalDataSource.setSelectedStyleId(validStyleId)
    }

    override suspend fun refreshRemoteStyles(): Result<Unit> =
        runCatching {
            val remoteStyles = remoteRemoteDataSource.fetchStyles()
            remoteLocalDataSource.replaceCachedStyles(remoteStyles)
        }

    private fun observeMergedStyles(): Flow<List<MapStyleRecord>> =
        remoteLocalDataSource.observeCachedStyles().map { cachedRemoteStyles ->
            val bundledStyles = bundledDataSource.getStyles()
            val bundledIds = bundledStyles.map { it.id }.toSet()
            val distinctRemoteStyles = cachedRemoteStyles.filterNot { it.id in bundledIds }
            bundledStyles + distinctRemoteStyles
        }
}
