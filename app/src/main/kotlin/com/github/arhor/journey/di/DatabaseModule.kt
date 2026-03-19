package com.github.arhor.journey.di

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
            .addMigrations()
            .build()

    @Provides
    fun provideHeroDao(db: JourneyDatabase): HeroDao = db.heroDao()

    @Provides
    fun provideHeroResourceDao(db: JourneyDatabase): HeroResourceDao = db.heroResourceDao()

    @Provides
    fun provideCollectedResourceSpawnDao(db: JourneyDatabase): CollectedResourceSpawnDao =
        db.collectedResourceSpawnDao()

    @Provides
    fun providePoiDao(db: JourneyDatabase): PoiDao = db.poiDao()

    @Provides
    fun provideDiscoveredPoiDao(db: JourneyDatabase): DiscoveredPoiDao = db.discoveredPoiDao()

    @Provides
    fun provideExplorationTileDao(db: JourneyDatabase): ExplorationTileDao = db.explorationTileDao()

    @Provides
    @Singleton
    fun provideTransactionRunner(db: JourneyDatabase): TransactionRunner = RoomTransactionRunner(db)
}
