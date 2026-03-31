package com.github.arhor.journey.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.data.local.db.dao.CollectedResourceSpawnDao
import com.github.arhor.journey.data.local.db.dao.DiscoveredPoiDao
import com.github.arhor.journey.data.local.db.dao.ExplorationTileDao
import com.github.arhor.journey.data.local.db.dao.HeroDao
import com.github.arhor.journey.data.local.db.dao.HeroResourceDao
import com.github.arhor.journey.data.local.db.dao.PoiDao
import com.github.arhor.journey.data.local.db.entity.CollectedResourceSpawnEntity
import com.github.arhor.journey.data.local.db.entity.DiscoveredPoiEntity
import com.github.arhor.journey.data.local.db.entity.ExploredTileEntity
import com.github.arhor.journey.data.local.db.entity.HeroEntity
import com.github.arhor.journey.data.local.db.entity.HeroResourceEntity
import com.github.arhor.journey.data.local.db.entity.PoiEntity

@Database(
    entities = [
        HeroEntity::class,
        HeroResourceEntity::class,
        CollectedResourceSpawnEntity::class,
        PoiEntity::class,
        DiscoveredPoiEntity::class,
        ExploredTileEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(
    value = [
        InstantTypeConverter::class,
    ]
)
abstract class JourneyDatabase : RoomDatabase() {

    abstract fun heroDao(): HeroDao

    abstract fun heroResourceDao(): HeroResourceDao

    abstract fun collectedResourceSpawnDao(): CollectedResourceSpawnDao

    abstract fun poiDao(): PoiDao

    abstract fun discoveredPoiDao(): DiscoveredPoiDao

    abstract fun explorationTileDao(): ExplorationTileDao

    companion object {
        val MIGRATIONS: Array<Migration>
            get() = arrayOf(MIGRATION_1_2)

        private val MIGRATION_1_2 = Migration(1, 2) { db ->
            val legacyResourceIdMappings = listOf(
                "wood" to ResourceType.SCRAP,
                "stone" to ResourceType.COMPONENTS,
                "coal" to ResourceType.FUEL,
            )

            fun remapTypeIds(
                tableName: String,
                columnName: String = "typeId",
            ) {
                legacyResourceIdMappings.forEach { (oldTypeId, newType) ->
                    db.execSQL(
                        """
                        UPDATE $tableName
                        SET $columnName = '${newType.typeId}'
                        WHERE $columnName = '$oldTypeId'
                        """.trimIndent(),
                    )
                }
            }

            fun remapCollectedSpawnIds() {
                legacyResourceIdMappings.forEach { (oldTypeId, newType) ->
                    db.execSQL(
                        """
                        UPDATE collected_resource_spawns
                        SET spawnId = SUBSTR(spawnId, 1, length(spawnId) - ${oldTypeId.length}) || '${newType.typeId}'
                        WHERE spawnId LIKE '%:$oldTypeId'
                        """.trimIndent(),
                    )
                }
            }

            remapTypeIds(tableName = "hero_resources")
            remapTypeIds(tableName = "collected_resource_spawns")
            remapCollectedSpawnIds()
        }
    }
}
