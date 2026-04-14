package com.github.arhor.journey.feature.map.location

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {

    @Provides
    fun provideLocationStabilizerConfig(): LocationStabilizerConfig =
        LocationStabilizerConfig()
}
