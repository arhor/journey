package com.github.arhor.journey.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.arhor.journey.data.local.db.dao.ActivityLogDao
import com.github.arhor.journey.data.local.db.dao.DiscoveredPoiDao
import com.github.arhor.journey.data.local.db.dao.HeroDao
import com.github.arhor.journey.data.local.db.dao.PoiDao
import com.github.arhor.journey.data.local.db.entity.ActivityLogEntity
import com.github.arhor.journey.data.local.db.entity.DiscoveredPoiEntity
import com.github.arhor.journey.data.local.db.entity.HeroEntity
import com.github.arhor.journey.data.local.db.entity.PoiEntity

@Database(
    entities = [
        HeroEntity::class,
        ActivityLogEntity::class,
        PoiEntity::class,
        DiscoveredPoiEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class JourneyDatabase : RoomDatabase() {

    abstract fun heroDao(): HeroDao

    abstract fun activityLogDao(): ActivityLogDao

    abstract fun poiDao(): PoiDao

    abstract fun discoveredPoiDao(): DiscoveredPoiDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE activity_log ADD COLUMN external_record_id TEXT")
                db.execSQL("ALTER TABLE activity_log ADD COLUMN origin_package_name TEXT")
                db.execSQL("ALTER TABLE activity_log ADD COLUMN time_bounds_hash TEXT")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hero ADD COLUMN energy_current INTEGER NOT NULL DEFAULT 100")
                db.execSQL("ALTER TABLE hero ADD COLUMN energy_max INTEGER NOT NULL DEFAULT 100")
                db.execSQL("UPDATE hero SET energy_max = CASE WHEN energy_max < 1 THEN 1 ELSE energy_max END")
                db.execSQL(
                    """
                    UPDATE hero
                    SET energy_current = CASE
                        WHEN energy_current < 0 THEN 0
                        WHEN energy_current > energy_max THEN energy_max
                        ELSE energy_current
                    END
                    """.trimIndent(),
                )
            }
        }


        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE activity_log ADD COLUMN reward_energy_delta INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
