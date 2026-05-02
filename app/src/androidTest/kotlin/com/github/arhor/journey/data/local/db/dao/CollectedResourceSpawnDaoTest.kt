package com.github.arhor.journey.data.local.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.arhor.journey.data.local.db.JourneyDatabase
import com.github.arhor.journey.data.local.db.entity.CollectedResourceSpawnEntity
import com.github.arhor.journey.data.local.db.entity.HeroEntity
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class CollectedResourceSpawnDaoTest {

    private lateinit var database: JourneyDatabase
    private lateinit var heroDao: HeroDao
    private lateinit var collectedResourceSpawnDao: CollectedResourceSpawnDao

    @Before
    fun setUp() {
        runTest {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            database = Room.inMemoryDatabaseBuilder(context, JourneyDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            heroDao = database.heroDao()
            collectedResourceSpawnDao = database.collectedResourceSpawnDao()

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
            heroDao.upsert(
                HeroEntity(
                    id = "other",
                    name = "Scout",
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
    fun `insert should ignore duplicates for the same hero and spawn`() = runTest {
        // Given
        val firstClaim = CollectedResourceSpawnEntity(
            heroId = "player",
            spawnId = "spawn-1",
            typeId = "scrap",
            collectedAt = Instant.ofEpochMilli(1_700_000_100_000L),
        )

        // When
        val inserted = collectedResourceSpawnDao.insert(firstClaim)
        val duplicate = collectedResourceSpawnDao.insert(
            firstClaim.copy(
                typeId = "ore",
                collectedAt = Instant.ofEpochMilli(1_700_000_200_000L),
            ),
        )
        val otherHero = collectedResourceSpawnDao.insert(
            firstClaim.copy(
                heroId = "other",
                collectedAt = Instant.ofEpochMilli(1_700_000_300_000L),
            ),
        )

        // Then
        inserted shouldBe 1L
        duplicate shouldBe -1L
        otherHero shouldBe 2L
        collectedResourceSpawnDao.exists(heroId = "player", spawnId = "spawn-1") shouldBe true
        collectedResourceSpawnDao.observeAll(heroId = "player").first().shouldHaveSize(1)
        collectedResourceSpawnDao.observeAll(heroId = "other").first().shouldHaveSize(1)
    }
}
