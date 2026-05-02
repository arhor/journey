package com.github.arhor.journey.data.di

import com.github.arhor.journey.data.mapobject.LocalGeneratedMapObjectAreaSource
import com.github.arhor.journey.data.mapobject.MapObjectAreaSource
import com.github.arhor.journey.data.mapobject.WatchtowerDefinitionTileSource
import com.github.arhor.journey.data.repository.DeterministicResourceSpawnRepository
import com.github.arhor.journey.data.repository.DeterministicWatchtowerRepository
import com.github.arhor.journey.data.repository.DataStoreAppSettingsRepository
import com.github.arhor.journey.data.repository.RoomCollectedResourceSpawnRepository
import com.github.arhor.journey.data.repository.RoomExplorationTileRepository
import com.github.arhor.journey.data.repository.RoomHeroRepository
import com.github.arhor.journey.data.repository.RoomHeroResourcesRepository
import com.github.arhor.journey.domain.repository.AppSettingsRepository
import com.github.arhor.journey.domain.repository.CollectedResourceSpawnRepository
import com.github.arhor.journey.domain.repository.ExplorationTileRepository
import com.github.arhor.journey.domain.repository.HeroInventoryRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import com.github.arhor.journey.domain.repository.ResourceSpawnRepository
import com.github.arhor.journey.domain.repository.WatchtowerRepository
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
    fun bindWatchtowerDefinitionTileSource(impl: LocalGeneratedMapObjectAreaSource)
        : WatchtowerDefinitionTileSource

    @Binds
    fun bindHeroRepository(impl: RoomHeroRepository)
        : HeroRepository

    @Binds
    fun bindHeroInventoryRepository(impl: RoomHeroResourcesRepository)
        : HeroInventoryRepository

    @Binds
    fun bindCollectedResourceSpawnRepository(impl: RoomCollectedResourceSpawnRepository)
        : CollectedResourceSpawnRepository

    @Binds
    fun bindResourceSpawnRepository(impl: DeterministicResourceSpawnRepository)
        : ResourceSpawnRepository

    @Binds
    fun bindExplorationTileRepository(impl: RoomExplorationTileRepository)
        : ExplorationTileRepository

    @Binds
    fun bindWatchtowerRepository(impl: DeterministicWatchtowerRepository)
        : WatchtowerRepository

    @Binds
    fun bindAppSettingsRepository(impl: DataStoreAppSettingsRepository)
        : AppSettingsRepository

}
