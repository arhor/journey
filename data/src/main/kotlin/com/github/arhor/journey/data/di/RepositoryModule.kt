package com.github.arhor.journey.data.di

import com.github.arhor.journey.data.repository.DataStoreSettingsRepositoryImpl
import com.github.arhor.journey.data.repository.DeterministicResourceSpawnRepository
import com.github.arhor.journey.data.repository.MapStylesRepositoryImpl
import com.github.arhor.journey.data.repository.RoomCollectedResourceSpawnRepository
import com.github.arhor.journey.data.repository.RoomExplorationRepository
import com.github.arhor.journey.data.repository.RoomExplorationTileRepository
import com.github.arhor.journey.data.repository.RoomHeroRepository
import com.github.arhor.journey.data.repository.RoomHeroResourcesRepository
import com.github.arhor.journey.data.repository.RoomPointOfInterestRepository
import com.github.arhor.journey.domain.repository.CollectedResourceSpawnRepository
import com.github.arhor.journey.domain.repository.ExplorationRepository
import com.github.arhor.journey.domain.repository.ExplorationTileRepository
import com.github.arhor.journey.domain.repository.HeroInventoryRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import com.github.arhor.journey.domain.repository.MapStylesRepository
import com.github.arhor.journey.domain.repository.PointOfInterestRepository
import com.github.arhor.journey.domain.repository.ResourceSpawnRepository
import com.github.arhor.journey.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {

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
    fun bindPointOfInterestRepository(impl: RoomPointOfInterestRepository)
        : PointOfInterestRepository

    @Binds
    fun bindExplorationRepository(impl: RoomExplorationRepository)
        : ExplorationRepository

    @Binds
    fun bindExplorationTileRepository(impl: RoomExplorationTileRepository)
        : ExplorationTileRepository

    @Binds
    fun bindMapStylesRepository(impl: MapStylesRepositoryImpl)
        : MapStylesRepository

    @Binds
    fun bindSettingsRepository(impl: DataStoreSettingsRepositoryImpl)
        : SettingsRepository
}
