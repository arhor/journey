package com.github.arhor.journey.di

import com.github.arhor.journey.core.logging.LoggerFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LoggingModule {

    @Binds
    @Singleton
    abstract fun bindLoggerFactory(impl: AndroidLoggerFactory): LoggerFactory
}
