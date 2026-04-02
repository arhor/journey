package com.github.arhor.journey.data.local.db.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.arhor.journey.data.local.db.JourneyDatabase
import com.github.arhor.journey.data.local.db.entity.WatchtowerStateEntity
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class WatchtowerStateDaoTest {

    private lateinit var database: JourneyDatabase
    private lateinit var watchtowerStateDao: WatchtowerStateDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, JourneyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        watchtowerStateDao = database.watchtowerStateDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `observeByIds should emit sparse state rows ordered by watchtower id`() = runTest {
        // Given
        val towerA = stateEntity(watchtowerId = "watchtower:v1:15:17635:10747")
        val towerC = stateEntity(watchtowerId = "watchtower:v1:15:17635:10749")
        watchtowerStateDao.insertState(towerC)
        watchtowerStateDao.insertState(towerA)

        // When
        val actual = watchtowerStateDao.observeByIds(
            ids = listOf(
                "watchtower:v1:15:17635:10749",
                "watchtower:v1:15:17635:10747",
                "watchtower:v1:15:17635:10748",
            ),
        ).first()

        // Then
        actual.map(WatchtowerStateEntity::watchtowerId) shouldContainExactly listOf(
            "watchtower:v1:15:17635:10747",
            "watchtower:v1:15:17635:10749",
        )
    }

    @Test
    fun `markClaimed should update a dormant row only once`() = runTest {
        // Given
        watchtowerStateDao.insertState(stateEntity(watchtowerId = "watchtower:v1:15:17635:10747"))

        // When
        val firstUpdateCount = watchtowerStateDao.markClaimed(
            watchtowerId = "watchtower:v1:15:17635:10747",
            claimedAt = Instant.parse("2026-04-02T10:00:00Z"),
            level = 1,
            updatedAt = Instant.parse("2026-04-02T10:00:00Z"),
        )
        val secondUpdateCount = watchtowerStateDao.markClaimed(
            watchtowerId = "watchtower:v1:15:17635:10747",
            claimedAt = Instant.parse("2026-04-03T10:00:00Z"),
            level = 1,
            updatedAt = Instant.parse("2026-04-03T10:00:00Z"),
        )

        // Then
        firstUpdateCount shouldBe 1
        secondUpdateCount shouldBe 0
    }

    @Test
    fun `setLevel should update only claimed rows when the requested level increases`() = runTest {
        // Given
        watchtowerStateDao.insertState(
            stateEntity(
                watchtowerId = "watchtower:v1:15:17635:10747",
                claimedAt = Instant.parse("2026-04-02T10:00:00Z"),
                level = 1,
            ),
        )
        watchtowerStateDao.insertState(
            stateEntity(
                watchtowerId = "watchtower:v1:15:17635:10748",
                claimedAt = null,
                level = 0,
            ),
        )

        // When
        val claimedUpgradeCount = watchtowerStateDao.setLevel(
            watchtowerId = "watchtower:v1:15:17635:10747",
            level = 2,
            updatedAt = Instant.parse("2026-04-03T10:00:00Z"),
        )
        val repeatedUpgradeCount = watchtowerStateDao.setLevel(
            watchtowerId = "watchtower:v1:15:17635:10747",
            level = 2,
            updatedAt = Instant.parse("2026-04-04T10:00:00Z"),
        )
        val dormantUpgradeCount = watchtowerStateDao.setLevel(
            watchtowerId = "watchtower:v1:15:17635:10748",
            level = 1,
            updatedAt = Instant.parse("2026-04-03T10:00:00Z"),
        )

        // Then
        claimedUpgradeCount shouldBe 1
        repeatedUpgradeCount shouldBe 0
        dormantUpgradeCount shouldBe 0
    }

    private fun stateEntity(
        watchtowerId: String,
        claimedAt: Instant? = null,
        level: Int = 0,
    ) = WatchtowerStateEntity(
        watchtowerId = watchtowerId,
        discoveredAt = Instant.parse("2026-04-01T10:00:00Z"),
        claimedAt = claimedAt,
        level = level,
        updatedAt = Instant.parse("2026-04-01T10:00:00Z"),
    )
}
