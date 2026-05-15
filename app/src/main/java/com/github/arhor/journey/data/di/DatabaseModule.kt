package com.github.arhor.journey.data.di

import android.content.Context
import androidx.room.Room
import com.github.arhor.journey.data.local.db.JourneyDatabase
import com.github.arhor.journey.data.local.db.RoomTransactionRunner
import com.github.arhor.journey.data.local.db.dao.BreachNodeStateDao
import com.github.arhor.journey.data.local.db.dao.ExplorationTileDao
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
    fun provideExplorationTileDao(db: JourneyDatabase): ExplorationTileDao =
        db.explorationTileDao()

    @Provides
    @Singleton
    fun provideBreachNodeStateDao(db: JourneyDatabase): BreachNodeStateDao =
        db.breachNodeStateDao()

    @Provides
    @Singleton
    fun provideTransactionRunner(db: JourneyDatabase): TransactionRunner =
        RoomTransactionRunner(db)
}
