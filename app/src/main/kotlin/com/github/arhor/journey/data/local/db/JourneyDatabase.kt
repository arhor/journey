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
    version = 2,
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
    }
}
