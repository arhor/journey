package com.github.arhor.journey.data.di

import android.content.Context
import androidx.room.Room
import com.github.arhor.journey.data.local.db.JourneyDatabase
import com.github.arhor.journey.data.local.db.RoomTransactionRunner
import com.github.arhor.journey.data.local.db.dao.CollectedResourceSpawnDao
import com.github.arhor.journey.data.local.db.dao.DiscoveredPoiDao
import com.github.arhor.journey.data.local.db.dao.ExplorationTileDao
import com.github.arhor.journey.data.local.db.dao.HeroDao
import com.github.arhor.journey.data.local.db.dao.HeroResourceDao
import com.github.arhor.journey.data.local.db.dao.PoiDao
import com.github.arhor.journey.data.local.db.dao.WatchtowerStateDao
import com.github.arhor.journey.domain.TransactionRunner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideJourneyDatabase(
        @ApplicationContext context: Context,
    ): JourneyDatabase =
        Room.databaseBuilder(context, JourneyDatabase::class.java, "journey.db")
            .addMigrations(*JourneyDatabase.MIGRATIONS)
            .build()

    @Provides
    @Singleton
    fun provideHeroDao(db: JourneyDatabase): HeroDao =
        db.heroDao()

    @Provides
    @Singleton
    fun provideHeroResourceDao(db: JourneyDatabase): HeroResourceDao =
        db.heroResourceDao()

    @Provides
    @Singleton
    fun provideCollectedResourceSpawnDao(db: JourneyDatabase): CollectedResourceSpawnDao =
        db.collectedResourceSpawnDao()

    @Provides
    @Singleton
    fun providePoiDao(db: JourneyDatabase): PoiDao =
        db.poiDao()

    @Provides
    @Singleton
    fun provideDiscoveredPoiDao(db: JourneyDatabase): DiscoveredPoiDao =
        db.discoveredPoiDao()

    @Provides
    @Singleton
    fun provideExplorationTileDao(db: JourneyDatabase): ExplorationTileDao =
        db.explorationTileDao()

    @Provides
    @Singleton
    fun provideWatchtowerStateDao(db: JourneyDatabase): WatchtowerStateDao =
        db.watchtowerStateDao()

    @Provides
    @Singleton
    fun provideTransactionRunner(db: JourneyDatabase): TransactionRunner =
        RoomTransactionRunner(db)
}
