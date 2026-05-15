package com.github.arhor.journey.data.di

import com.github.arhor.journey.data.mapobject.LocalGeneratedMapObjectAreaSource
import com.github.arhor.journey.data.mapobject.MapObjectAreaSource
import com.github.arhor.journey.data.repository.DeterministicResourceSpawnRepository
import com.github.arhor.journey.data.repository.DataStoreAppSettingsRepository
import com.github.arhor.journey.data.repository.RoomExplorationTileRepository
import com.github.arhor.journey.domain.repository.AppSettingsRepository
import com.github.arhor.journey.domain.repository.ExplorationTileRepository
import com.github.arhor.journey.domain.repository.ResourceSpawnRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {

    @Binds
    fun bindMapObjectAreaSource(impl: LocalGeneratedMapObjectAreaSource)
        : MapObjectAreaSource

    @Binds
    fun bindResourceSpawnRepository(impl: DeterministicResourceSpawnRepository)
        : ResourceSpawnRepository

    @Binds
    fun bindExplorationTileRepository(impl: RoomExplorationTileRepository)
        : ExplorationTileRepository

    @Binds
    fun bindAppSettingsRepository(impl: DataStoreAppSettingsRepository)
        : AppSettingsRepository

}
