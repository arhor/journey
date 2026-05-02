package com.github.arhor.journey.feature.exploration.di

import com.github.arhor.journey.domain.tracking.ExplorationTrackingSessionController
import com.github.arhor.journey.feature.exploration.ExplorationTrackingSessionControllerImpl
import com.github.arhor.journey.feature.exploration.location.AndroidLocationPermissionChecker
import com.github.arhor.journey.feature.exploration.location.AndroidUserLocationSource
import com.github.arhor.journey.feature.exploration.location.LocationPermissionChecker
import com.github.arhor.journey.feature.exploration.location.UserLocationSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExplorationTrackingModule {

    @Binds
    @Singleton
    abstract fun bindExplorationTrackingSessionController(
        impl: ExplorationTrackingSessionControllerImpl,
    ): ExplorationTrackingSessionController

    @Binds
    @Singleton
    abstract fun bindLocationPermissionChecker(
        impl: AndroidLocationPermissionChecker,
    ): LocationPermissionChecker

    @Binds
    @Singleton
    abstract fun bindUserLocationSource(
        impl: AndroidUserLocationSource,
    ): UserLocationSource
}
