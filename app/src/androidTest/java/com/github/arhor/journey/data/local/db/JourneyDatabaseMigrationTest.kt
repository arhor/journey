package com.github.arhor.journey.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
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
    fun `migrate5To6 should drop legacy gameplay tables and create breach node state when schema cleanup runs`() {
        // Given
        val dbName = "journey-foundation-cleanup-migration-test"

        migrationHelper.createDatabase(dbName, 5).apply {
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
                    PRIMARY KEY(`heroId`, `typeId`)
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
                    PRIMARY KEY(`heroId`, `spawnId`)
                )
                """.trimIndent(),
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `watchtower_state` (
                    `watchtowerId` TEXT NOT NULL,
                    `discoveredAt` INTEGER NOT NULL,
                    `claimedAt` INTEGER,
                    `level` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`watchtowerId`)
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
                INSERT INTO `hero` (`id`, `name`, `level`, `xpInLevel`, `energyNow`, `energyMax`, `createdAt`, `updatedAt`)
                VALUES ('player', 'Legacy', 2, 12, 90, 100, 1700000000000, 1700000000000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO `hero_resources` (`heroId`, `typeId`, `amount`, `updatedAt`)
                VALUES ('player', 'scrap', 11, 1700000000000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO `collected_resource_spawns` (`heroId`, `typeId`, `spawnId`, `collectedAt`)
                VALUES ('player', 'scrap', 'resource-spawn:v1:20527:10:20:0:scrap', 1700000000000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO `watchtower_state` (`watchtowerId`, `discoveredAt`, `claimedAt`, `level`, `updatedAt`)
                VALUES ('tower-legacy', 1700000000000, 1700001000000, 2, 1700001000000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO `explored_tiles` (`zoom`, `x`, `y`)
                VALUES (16, 34567, 22345)
                """.trimIndent(),
            )
            close()
        }

        // When
        val migrated = migrationHelper.runMigrationsAndValidate(
            dbName,
            6,
            true,
            JourneyDatabase.Companion.MIGRATION_5_6,
        )

        // Then
        migrated.hasTable("hero").shouldBeFalse()
        migrated.hasTable("hero_resources").shouldBeFalse()
        migrated.hasTable("collected_resource_spawns").shouldBeFalse()
        migrated.hasTable("watchtower_state").shouldBeFalse()
        migrated.hasTable("breach_node_state").shouldBeTrue()
        migrated.hasIndex("index_breach_node_state_h3CellId").shouldBeTrue()
        migrated.hasTable("explored_tiles").shouldBeTrue()
        migrated.rowCount("breach_node_state") shouldBe 0

        migrated.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(dbName) shouldBe true
    }

    private fun SupportSQLiteDatabase.hasTable(name: String): Boolean =
        query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$name'").use { cursor ->
            cursor.moveToFirst()
        }

    private fun SupportSQLiteDatabase.hasIndex(name: String): Boolean =
        query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = '$name'").use { cursor ->
            cursor.moveToFirst()
        }

    private fun SupportSQLiteDatabase.rowCount(tableName: String): Int =
        query("SELECT COUNT(*) FROM `$tableName`").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}
