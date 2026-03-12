package com.github.arhor.journey.di

import com.github.arhor.journey.data.repository.MapStylesRepositoryImpl
import com.github.arhor.journey.data.repository.RoomActivityLogRepository
import com.github.arhor.journey.data.repository.RoomExplorationRepository
import com.github.arhor.journey.data.repository.RoomHeroRepository
import com.github.arhor.journey.data.repository.RoomPointOfInterestRepository
import com.github.arhor.journey.domain.activity.repository.ActivityLogRepository
import com.github.arhor.journey.domain.exploration.repository.ExplorationRepository
import com.github.arhor.journey.domain.player.repository.HeroRepository
import com.github.arhor.journey.domain.map.repository.MapStylesRepository
import com.github.arhor.journey.domain.exploration.repository.PointOfInterestRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindHeroRepository(impl: RoomHeroRepository): HeroRepository

    @Binds
    @Singleton
    abstract fun bindActivityLogRepository(impl: RoomActivityLogRepository): ActivityLogRepository

    @Binds
    @Singleton
    abstract fun bindPointOfInterestRepository(impl: RoomPointOfInterestRepository): PointOfInterestRepository

    @Binds
    @Singleton
    abstract fun bindExplorationRepository(impl: RoomExplorationRepository): ExplorationRepository

    @Binds
    @Singleton
    abstract fun bindMapStylesRepository(impl: MapStylesRepositoryImpl): MapStylesRepository
}
