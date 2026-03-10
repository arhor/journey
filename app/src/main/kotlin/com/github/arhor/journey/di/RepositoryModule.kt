package com.github.arhor.journey.di

import com.github.arhor.journey.data.repository.RoomActivityLogRepository
import com.github.arhor.journey.data.repository.RoomExplorationRepository
import com.github.arhor.journey.data.repository.RoomHeroRepository
import com.github.arhor.journey.data.healthconnect.HealthConnectAvailabilityChecker
import com.github.arhor.journey.data.repository.RoomPointOfInterestRepository
import com.github.arhor.journey.data.repository.DataStoreHealthSyncCheckpointRepository
import com.github.arhor.journey.data.repository.HealthConnectPermissionRepositoryImpl
import com.github.arhor.journey.data.repository.HealthDataSyncRepositoryImpl
import com.github.arhor.journey.data.repository.MapStyleRepositoryImpl
import com.github.arhor.journey.domain.repository.ActivityLogRepository
import com.github.arhor.journey.domain.repository.ExplorationRepository
import com.github.arhor.journey.domain.repository.HealthConnectAvailabilityRepository
import com.github.arhor.journey.domain.repository.HealthDataSyncRepository
import com.github.arhor.journey.domain.repository.HealthPermissionRepository
import com.github.arhor.journey.domain.repository.HealthSyncCheckpointRepository
import com.github.arhor.journey.domain.repository.HeroRepository
import com.github.arhor.journey.domain.repository.MapStyleRepository
import com.github.arhor.journey.domain.repository.PointOfInterestRepository
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
    abstract fun bindHealthConnectAvailabilityRepository(
        impl: HealthConnectAvailabilityChecker,
    ): HealthConnectAvailabilityRepository

    @Binds
    @Singleton
    abstract fun bindHealthDataSyncRepository(
        impl: HealthDataSyncRepositoryImpl,
    ): HealthDataSyncRepository

    @Binds
    @Singleton
    abstract fun bindHealthPermissionRepository(
        impl: HealthConnectPermissionRepositoryImpl,
    ): HealthPermissionRepository

    @Binds
    @Singleton
    abstract fun bindHealthSyncCheckpointRepository(
        impl: DataStoreHealthSyncCheckpointRepository,
    ): HealthSyncCheckpointRepository

    @Binds
    @Singleton
    abstract fun bindMapStyleRepository(impl: MapStyleRepositoryImpl): MapStyleRepository
}
