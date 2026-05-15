package com.github.arhor.journey.data.di

import com.github.arhor.journey.data.spatial.UberH3Grid
import com.github.arhor.journey.domain.spatial.H3Grid
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface SpatialModule {

    @Binds
    fun bindH3Grid(impl: UberH3Grid): H3Grid
}
