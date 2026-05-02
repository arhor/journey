package com.github.arhor.journey.feature.map.prewarm

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MapTilePrewarmModule {

    @Binds
    @Singleton
    abstract fun bindMapTilePrewarmer(
        impl: MapTilePrewarmerImpl,
    ): MapTilePrewarmer

    @Binds
    @Singleton
    abstract fun bindMapTileResourceFetcher(
        impl: HttpMapTileResourceFetcher,
    ): MapTileResourceFetcher

    @Binds
    @Singleton
    abstract fun bindMapTileCacheWriter(
        impl: AndroidMapTileCacheWriter,
    ): MapTileCacheWriter
}
