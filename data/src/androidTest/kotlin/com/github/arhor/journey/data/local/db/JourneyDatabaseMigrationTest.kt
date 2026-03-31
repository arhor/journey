package com.github.arhor.journey.data.local.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.arhor.journey.data.local.db.dao.CollectedResourceSpawnDao
import com.github.arhor.journey.data.local.db.dao.HeroResourceDao
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JourneyDatabaseMigrationTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        JourneyDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_should_remap_legacy_resource_ids_and_spawn_suffixes() {
        val dbName = "journey-migration-test"

        migrationHelper.createDatabase(dbName, 1).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `hero` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `level` INTEGER NOT NULL,
                    `xpInLevel` INTEGER NOT NULL,
                    `energyNow` INTEGER NOT NULL,
                    `energyMax` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `hero_resources` (
                    `heroId` TEXT NOT NULL,
                    `typeId` TEXT NOT NULL,
                    `amount` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`heroId`, `typeId`),
                    FOREIGN KEY(`heroId`) REFERENCES `hero`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `collected_resource_spawns` (
                    `heroId` TEXT NOT NULL,
                    `typeId` TEXT NOT NULL,
                    `spawnId` TEXT NOT NULL,
                    `collectedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`heroId`, `spawnId`),
                    FOREIGN KEY(`heroId`) REFERENCES `hero`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_collected_resource_spawns_heroId_collectedAt`
                ON `collected_resource_spawns` (`heroId`, `collectedAt`)
                """.trimIndent(),
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `poi` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `description` TEXT,
                    `category` TEXT NOT NULL,
                    `lat` REAL NOT NULL,
                    `lon` REAL NOT NULL,
                    `radiusMeters` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `discovered_poi` (
                    `poiId` INTEGER NOT NULL,
                    `discoveredAt` INTEGER NOT NULL,
                    PRIMARY KEY(`poiId`),
                    FOREIGN KEY(`poiId`) REFERENCES `poi`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `explored_tiles` (
                    `zoom` INTEGER NOT NULL,
                    `x` INTEGER NOT NULL,
                    `y` INTEGER NOT NULL,
                    PRIMARY KEY(`zoom`, `x`, `y`)
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO hero (`id`, `name`, `level`, `xpInLevel`, `energyNow`, `energyMax`, `createdAt`, `updatedAt`)
                VALUES ('player', 'Adventurer', 1, 0, 100, 100, 1700000000000, 1700000000000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO hero_resources (`heroId`, `typeId`, `amount`, `updatedAt`)
                VALUES
                    ('player', 'wood', 3, 1700000100000),
                    ('player', 'stone', 5, 1700000200000),
                    ('player', 'coal', 7, 1700000300000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO collected_resource_spawns (`heroId`, `typeId`, `spawnId`, `collectedAt`)
                VALUES
                    ('player', 'wood', 'resource-spawn:v1:20527:10:20:0:wood', 1700000400000),
                    ('player', 'stone', 'resource-spawn:v1:20527:10:20:1:stone', 1700000500000),
                    ('player', 'coal', 'resource-spawn:v1:20527:10:20:2:coal', 1700000600000)
                """.trimIndent(),
            )
            close()
        }

        val migrated = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            JourneyDatabase::class.java,
            dbName,
        ).addMigrations(*JourneyDatabase.MIGRATIONS)
            .build()

        runBlocking {
            val heroResourceDao = migrated.heroResourceDao()
            val collectedResourceSpawnDao = migrated.collectedResourceSpawnDao()

            heroResourcesSnapshot(heroResources = heroResourceDao) shouldContainExactlyInAnyOrder listOf(
                "components" to 5,
                "fuel" to 7,
                "scrap" to 3,
            )
            collectedSpawnsSnapshot(collectedResourceSpawnDao = collectedResourceSpawnDao)
                .shouldContainExactlyInAnyOrder(
                    "resource-spawn:v1:20527:10:20:0:scrap" to "scrap",
                    "resource-spawn:v1:20527:10:20:1:components" to "components",
                    "resource-spawn:v1:20527:10:20:2:fuel" to "fuel",
                )
            heroResourceDao.getAmount(heroId = "player", typeId = "scrap") shouldBe 3
            heroResourceDao.getAmount(heroId = "player", typeId = "components") shouldBe 5
            heroResourceDao.getAmount(heroId = "player", typeId = "fuel") shouldBe 7
            heroResourceDao.getAmount(heroId = "player", typeId = "wood") shouldBe null
            collectedResourceSpawnDao.exists(
                heroId = "player",
                spawnId = "resource-spawn:v1:20527:10:20:0:scrap",
            ).shouldBeTrue()
            collectedResourceSpawnDao.exists(
                heroId = "player",
                spawnId = "resource-spawn:v1:20527:10:20:0:wood",
            ).shouldBeFalse()
        }

        migrated.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(dbName) shouldBe true
    }

    private suspend fun heroResourcesSnapshot(heroResources: HeroResourceDao): List<Pair<String, Int>> =
        heroResources.observeAll(heroId = "player")
            .first()
            .map { it.typeId to it.amount }

    private suspend fun collectedSpawnsSnapshot(collectedResourceSpawnDao: CollectedResourceSpawnDao): List<Pair<String, String>> =
        collectedResourceSpawnDao.observeAll(heroId = "player")
            .first()
            .map { it.spawnId to it.typeId }
}
