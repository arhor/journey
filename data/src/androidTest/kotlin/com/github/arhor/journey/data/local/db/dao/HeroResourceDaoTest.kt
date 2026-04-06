package com.github.arhor.journey.data.local.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.arhor.journey.data.local.db.JourneyDatabase
import com.github.arhor.journey.data.local.db.entity.HeroEntity
import com.github.arhor.journey.data.local.db.entity.HeroResourceEntity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class HeroResourceDaoTest {

    private lateinit var database: JourneyDatabase
    private lateinit var heroDao: HeroDao
    private lateinit var heroResourceDao: HeroResourceDao

    @Before
    fun setUp() {
        runTest {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            database = Room.inMemoryDatabaseBuilder(context, JourneyDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            heroDao = database.heroDao()
            heroResourceDao = database.heroResourceDao()

            heroDao.upsert(
                HeroEntity(
                    id = "player",
                    name = "Adventurer",
                    level = 1,
                    xpInLevel = 0L,
                    energyNow = 100,
                    energyMax = 100,
                    createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
                    updatedAt = Instant.ofEpochMilli(1_700_000_000_000L),
                ),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `incrementAmount should insert new row and accumulate subsequent increments`() = runTest {
        // When
        heroResourceDao.incrementAmount(
            heroId = "player",
            typeId = "scrap",
            amountDelta = 1,
            updatedAt = Instant.ofEpochMilli(1_700_000_100_000L),
        )
        heroResourceDao.incrementAmount(
            heroId = "player",
            typeId = "scrap",
            amountDelta = 2,
            updatedAt = Instant.ofEpochMilli(1_700_000_200_000L),
        )

        // Then
        heroResourceDao.getAmount(
            heroId = "player",
            typeId = "scrap",
        ) shouldBe 3
        heroResourceDao.observeAll(heroId = "player").first() shouldBe listOf(
            HeroResourceEntity(
                heroId = "player",
                typeId = "scrap",
                amount = 3,
                updatedAt = Instant.ofEpochMilli(1_700_000_200_000L),
            ),
        )
    }

    @Test
    fun `decrementAmountIfEnough should prevent underflow and keep the previous amount`() = runTest {
        // Given
        heroResourceDao.upsert(
            HeroResourceEntity(
                heroId = "player",
                typeId = "ore",
                amount = 2,
                updatedAt = Instant.ofEpochMilli(1_700_000_100_000L),
            ),
        )

        // When
        val success = heroResourceDao.decrementAmountIfEnough(
            heroId = "player",
            typeId = "ore",
            amountDelta = 1,
            updatedAt = Instant.ofEpochMilli(1_700_000_200_000L),
        )
        val failure = heroResourceDao.decrementAmountIfEnough(
            heroId = "player",
            typeId = "ore",
            amountDelta = 2,
            updatedAt = Instant.ofEpochMilli(1_700_000_300_000L),
        )

        // Then
        success shouldBe 1
        failure shouldBe 0
        heroResourceDao.getAmount(
            heroId = "player",
            typeId = "ore",
        ) shouldBe 1
    }
}
