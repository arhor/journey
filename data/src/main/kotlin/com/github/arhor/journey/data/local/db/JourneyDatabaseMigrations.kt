package com.github.arhor.journey.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object JourneyDatabaseMigrations {

    val MIGRATION_5_7: Migration = object : Migration(5, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""ALTER TABLE hero RENAME COLUMN created_at_ms TO created_at""")
            db.execSQL("""ALTER TABLE hero RENAME COLUMN updated_at_ms TO updated_at""")
            db.execSQL("""ALTER TABLE discovered_poi RENAME COLUMN discovered_at_ms TO discovered_at""")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS hero_resources (
                    hero_id TEXT NOT NULL,
                    resource_type_id TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY(hero_id, resource_type_id),
                    FOREIGN KEY(hero_id) REFERENCES hero(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS collected_resource_spawns (
                    hero_id TEXT NOT NULL,
                    spawn_id TEXT NOT NULL,
                    resource_type_id TEXT NOT NULL,
                    collected_at INTEGER NOT NULL,
                    PRIMARY KEY(hero_id, spawn_id),
                    FOREIGN KEY(hero_id) REFERENCES hero(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )

            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_collected_resource_spawns_hero_id_collected_at
                ON collected_resource_spawns(hero_id, collected_at)
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""ALTER TABLE hero RENAME COLUMN created_at_ms TO created_at""")
            db.execSQL("""ALTER TABLE hero RENAME COLUMN updated_at_ms TO updated_at""")
            db.execSQL("""ALTER TABLE discovered_poi RENAME COLUMN discovered_at_ms TO discovered_at""")
            db.execSQL("""ALTER TABLE hero_resources RENAME COLUMN updated_at_ms TO updated_at""")
            db.execSQL("""DROP INDEX IF EXISTS index_collected_resource_spawns_hero_id_collected_at_ms""")
            db.execSQL("""ALTER TABLE collected_resource_spawns RENAME COLUMN collected_at_ms TO collected_at""")
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_collected_resource_spawns_hero_id_collected_at
                ON collected_resource_spawns(hero_id, collected_at)
                """.trimIndent(),
            )
        }
    }
}
