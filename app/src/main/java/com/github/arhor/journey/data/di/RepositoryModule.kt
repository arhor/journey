package com.github.arhor.journey.data.di

import com.github.arhor.journey.data.repository.DataStoreAppSettingsRepository
import com.github.arhor.journey.data.repository.RoomExplorationTileRepository
import com.github.arhor.journey.domain.repository.AppSettingsRepository
import com.github.arhor.journey.domain.repository.ExplorationTileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {

    @Binds
    fun bindExplorationTileRepository(impl: RoomExplorationTileRepository)
        : ExplorationTileRepository

    @Binds
    fun bindAppSettingsRepository(impl: DataStoreAppSettingsRepository)
        : AppSettingsRepository

}
