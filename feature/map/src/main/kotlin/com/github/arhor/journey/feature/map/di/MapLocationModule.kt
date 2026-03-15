package com.github.arhor.journey.feature.map.di

import com.github.arhor.journey.feature.map.location.AndroidForegroundUserLocationTracker
import com.github.arhor.journey.feature.map.location.ForegroundUserLocationTracker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MapLocationModule {

    @Binds
    @Singleton
    abstract fun bindForegroundUserLocationTracker(
        impl: AndroidForegroundUserLocationTracker,
    ): ForegroundUserLocationTracker
}
