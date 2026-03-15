package com.github.arhor.journey.di

import com.github.arhor.journey.data.local.db.JourneyDatabase
import com.github.arhor.journey.data.local.db.RoomTransactionRunner
import com.github.arhor.journey.data.local.db.dao.ActivityLogDao
import com.github.arhor.journey.data.local.db.dao.DiscoveredPoiDao
import com.github.arhor.journey.data.local.db.dao.HeroDao
import com.github.arhor.journey.data.local.db.dao.PoiDao
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class DatabaseModuleTest {

    @Test
    fun `dao providers should return dao instances from database`() {
        // Given
        val db = mockk<JourneyDatabase>()
        val heroDao = mockk<HeroDao>()
        val activityLogDao = mockk<ActivityLogDao>()
        val poiDao = mockk<PoiDao>()
        val discoveredPoiDao = mockk<DiscoveredPoiDao>()

        every { db.heroDao() } returns heroDao
        every { db.activityLogDao() } returns activityLogDao
        every { db.poiDao() } returns poiDao
        every { db.discoveredPoiDao() } returns discoveredPoiDao

        // When
        val providedHeroDao = DatabaseModule.provideHeroDao(db)
        val providedActivityLogDao = DatabaseModule.provideActivityLogDao(db)
        val providedPoiDao = DatabaseModule.providePoiDao(db)
        val providedDiscoveredPoiDao = DatabaseModule.provideDiscoveredPoiDao(db)

        // Then
        providedHeroDao shouldBe heroDao
        providedActivityLogDao shouldBe activityLogDao
        providedPoiDao shouldBe poiDao
        providedDiscoveredPoiDao shouldBe discoveredPoiDao
    }

    @Test
    fun `dao providers should propagate exception when database access fails`() {
        // Given
        val db = mockk<JourneyDatabase>()
        every { db.activityLogDao() } throws IllegalStateException("database is not ready")

        // When
        val exception = shouldThrow<IllegalStateException> {
            DatabaseModule.provideActivityLogDao(db)
        }

        // Then
        exception.message shouldBe "database is not ready"
    }

    @Test
    fun `provideTransactionRunner should return room transaction runner`() {
        // Given
        val db = mockk<JourneyDatabase>()

        // When
        val transactionRunner = DatabaseModule.provideTransactionRunner(db)

        // Then
        (transactionRunner is RoomTransactionRunner) shouldBe true
    }
}
