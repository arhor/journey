# Breach Protocol Epic Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace watchtowers, the old character-summary foundation, and player-stat budget concepts with an H3-backed Breach Protocol loop.

**Architecture:** Execute the epic as buildable tickets. First remove the old feature foundations and database tables, then add H3 spatial primitives, breach persistence, breach rules, map session state, Compose overlays, marker rendering, and visible fog reveal.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, Kotlin coroutines/Flow, MapLibre, `com.uber:h3-android`, Kotest, MockK, Android instrumentation tests.

---

## Scope And Sequencing

The approved spec is [2026-05-14-breach-protocol-design.md](../specs/2026-05-14-breach-protocol-design.md). This epic is intentionally split into tickets. Each ticket must compile and commit before the next ticket starts.

Ticket order:

1. Initial gameplay foundation cleanup.
2. H3 spatial adapter and breach domain persistence.
3. Breach scan, discovery, completion, and reveal rules.
4. Map ViewModel session flow.
5. Breach Compose overlays.
6. Dynamic breach map object rendering.
7. Visible controlled-sector fog reveal.
8. Runtime validation and polish backlog capture.

Do not start Ticket 2 until Ticket 1 passes `./gradlew :app:compileDebugKotlin -q`.

## File Structure Map

Ticket 1 removes old feature files and updates shared wiring:

- Delete: `app/src/main/java/com/github/arhor/journey/feature/hero/**`
- Delete: `app/src/main/java/com/github/arhor/journey/domain/internal/Watchtower*.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/domain/model/Watchtower*.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/domain/model/Hero.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/domain/model/HeroEnergy.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/domain/model/Progression.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/domain/repository/HeroRepository.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/domain/repository/HeroInventoryRepository.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/domain/repository/WatchtowerRepository.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/domain/usecase/*Watchtower*.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/domain/usecase/*Hero*.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/domain/usecase/*Resource*.kt` when the use case only mutates player inventory.
- Delete: `app/src/main/java/com/github/arhor/journey/domain/model/error/*Watchtower*.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/domain/model/error/HeroResourcesError.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/data/local/db/dao/HeroDao.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/data/local/db/dao/HeroResourceDao.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/data/local/db/dao/CollectedResourceSpawnDao.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/data/local/db/dao/WatchtowerStateDao.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/data/local/db/entity/HeroEntity.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/data/local/db/entity/HeroResourceEntity.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/data/local/db/entity/CollectedResourceSpawnEntity.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/data/local/db/entity/WatchtowerStateEntity.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/data/local/seed/DefaultHeroSeed.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/data/mapper/HeroMapper.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/data/mapper/WatchtowerMapper.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/data/repository/RoomHeroRepository.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/data/repository/RoomHeroResourcesRepository.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/data/repository/RoomCollectedResourceSpawnRepository.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/data/repository/DeterministicWatchtowerRepository.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/data/mapobject/WatchtowerDefinitionTileSource.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/feature/map/WatchtowerBottomSheet.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/feature/map/WatchtowerSheetUiState.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/feature/map/model/WatchtowerMarkerState.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/feature/map/presentation/SelectedWatchtowerPresenter.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/data/local/db/JourneyDatabase.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/data/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/data/di/RepositoryModule.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/data/mapobject/*.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapViewModel.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapUiState.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapIntent.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapRoute.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/ui/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/ui/navigation/TopLevelNavigation.kt`
- Modify: `app/src/main/res/values/strings.xml`

Tickets 2-7 add the new Breach Protocol files:

- Create: `app/src/main/java/com/github/arhor/journey/domain/spatial/H3Grid.kt`
- Create: `app/src/main/java/com/github/arhor/journey/data/spatial/UberH3Grid.kt`
- Create: `app/src/main/java/com/github/arhor/journey/data/di/SpatialModule.kt`
- Create: `app/src/main/java/com/github/arhor/journey/domain/internal/BreachBalance.kt`
- Create: `app/src/main/java/com/github/arhor/journey/domain/internal/BreachNodeGeneration.kt`
- Create: `app/src/main/java/com/github/arhor/journey/domain/internal/BreachMapping.kt`
- Create: `app/src/main/java/com/github/arhor/journey/domain/model/BreachNodeDefinition.kt`
- Create: `app/src/main/java/com/github/arhor/journey/domain/model/BreachNodeState.kt`
- Create: `app/src/main/java/com/github/arhor/journey/domain/model/BreachNodeRecord.kt`
- Create: `app/src/main/java/com/github/arhor/journey/domain/model/BreachNode.kt`
- Create: `app/src/main/java/com/github/arhor/journey/domain/model/BreachNodePhase.kt`
- Create: `app/src/main/java/com/github/arhor/journey/domain/model/ControlledBreachRevealSnapshot.kt`
- Create: `app/src/main/java/com/github/arhor/journey/domain/model/error/BreachNodeError.kt`
- Create: `app/src/main/java/com/github/arhor/journey/domain/repository/BreachNodeRepository.kt`
- Create: `app/src/main/java/com/github/arhor/journey/data/local/db/entity/BreachNodeStateEntity.kt`
- Create: `app/src/main/java/com/github/arhor/journey/data/local/db/dao/BreachNodeStateDao.kt`
- Create: `app/src/main/java/com/github/arhor/journey/data/mapper/BreachNodeMapper.kt`
- Create: `app/src/main/java/com/github/arhor/journey/data/repository/DeterministicBreachNodeRepository.kt`
- Create: `app/src/main/java/com/github/arhor/journey/domain/usecase/FindNearestBreachNodeUseCase.kt`
- Create: `app/src/main/java/com/github/arhor/journey/domain/usecase/DiscoverBreachNodeUseCase.kt`
- Create: `app/src/main/java/com/github/arhor/journey/domain/usecase/CompleteBreachUseCase.kt`
- Create: `app/src/main/java/com/github/arhor/journey/domain/usecase/ObserveControlledBreachRevealCellsUseCase.kt`
- Create: `app/src/main/java/com/github/arhor/journey/domain/usecase/ObserveVisibleBreachNodesUseCase.kt`
- Create: `app/src/main/java/com/github/arhor/journey/feature/map/BreachProtocolUiState.kt`
- Create: `app/src/main/java/com/github/arhor/journey/feature/map/BreachProtocolOverlay.kt`
- Create: `app/src/main/java/com/github/arhor/journey/feature/map/presentation/BreachNodePresenter.kt`
- Create: tests matching each new production unit.

## Ticket 1: Initial Gameplay Foundation Cleanup

### Task 1.1: Remove top-level character navigation

**Files:**

- Delete: `app/src/main/java/com/github/arhor/journey/feature/hero/HeroRoute.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/feature/hero/HeroNavigationContract.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/feature/hero/HeroScreen.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/feature/hero/HeroViewModel.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/feature/hero/HeroUiState.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/feature/hero/HeroIntent.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/feature/hero/HeroEffect.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/feature/hero/components/LoadingIndicator.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/feature/hero/components/ResourceRow.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/feature/hero/components/StatRow.kt`
- Delete: `app/src/test/java/com/github/arhor/journey/feature/hero/HeroViewModelTest.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/ui/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapRoute.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [x] **Step 1: Write the expected navigation shape**

Edit `AppNavGraph.kt` to remove the character graph import and keep only map/settings graphs:

```kotlin
NavHost(
    navController = controller,
    startDestination = MapDestination,
    modifier = Modifier.padding(innerPadding),
) {
    mapGraph(snackbarHostState = snackbarHostState)
    settingsGraph(snackbarHostState = snackbarHostState)
}
```

Edit `MapRoute.kt` signature:

```kotlin
fun MapRoute(
    vm: MapViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState,
)
```

- [x] **Step 2: Run compile and observe unresolved references**

Run: `./gradlew :app:compileDebugKotlin -q`

Expected: FAIL with unresolved `HeroDestination`, `heroGraph`, or `onOpenHero` references before all imports and callers are cleaned.

- [x] **Step 3: Delete the character feature files and strings**

Remove the listed `feature/hero` files. Remove these strings from `app/src/main/res/values/strings.xml`:

```xml
<string name="nav_hero">Hero</string>
<string name="hero_hero_summary_title">Hero Summary</string>
<string name="hero_hero_level_value">Level %1$d</string>
<string name="hero_progress_title">Progression</string>
<string name="hero_progress_xp_value">XP: %1$d / %2$d</string>
<string name="hero_resources_title">Resources</string>
<string name="hero_nav_label">Hero</string>
```

- [x] **Step 4: Run focused compile**

Run: `./gradlew :app:compileDebugKotlin -q`

Expected: FAIL only on remaining domain/data references removed by Task 1.2, Task 1.3, and Task 1.4.

- [x] **Step 5: Commit**

Run:

```bash
git add app/src/main/java/com/github/arhor/journey app/src/main/res/values/strings.xml app/src/test/java/com/github/arhor/journey/feature/hero
git commit -m "refactor: remove character summary navigation"
```

### Task 1.2: Remove watchtower domain, map UI, and repository wiring

**Files:**

- Delete all watchtower files listed in the File Structure Map.
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapViewModel.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapUiState.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapIntent.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/model/MapObjectUiModel.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/presentation/MapWorldObjectPresenter.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/data/di/RepositoryModule.kt`

- [x] **Step 1: Remove watchtower state from map state types**

In `MapUiState.Content`, remove:

```kotlin
val selectedWatchtower: WatchtowerSheetUiState?,
```

In `MapIntent`, remove:

```kotlin
data object DismissWatchtowerSheet : MapIntent
data object ClaimSelectedWatchtower : MapIntent
data object UpgradeSelectedWatchtower : MapIntent
```

- [x] **Step 2: Remove watchtower dependencies from `MapViewModel` constructor**

Delete constructor dependencies:

```kotlin
private val observeVisibleWatchtowers: ObserveVisibleWatchtowersUseCase,
private val observeHeroResourceAmount: ObserveHeroResourceAmountUseCase,
private val claimWatchtower: ClaimWatchtowerUseCase,
private val upgradeWatchtower: UpgradeWatchtowerUseCase,
private val getWatchtower: GetWatchtowerUseCase,
private val selectedWatchtowerPresenter: SelectedWatchtowerPresenter,
```

Remove watchtower selected state fields from private `State`.

- [x] **Step 3: Keep map content compiling with resources only**

Temporarily reduce `observeVisibleWorldObjects()` to resource spawn objects only:

```kotlin
private fun observeVisibleWorldObjects(): Flow<Output<VisibleWorldObjects, DomainError>> =
    observeVisibleResourceSpawnObjects()
        .map { output ->
            output.map { resourceSpawnObjects ->
                VisibleWorldObjects(objects = resourceSpawnObjects)
            }
        }
```

Keep this until Ticket 6 adds breach node objects.

- [x] **Step 4: Remove watchtower bindings**

Delete these bindings from `RepositoryModule.kt`:

```kotlin
fun bindWatchtowerDefinitionTileSource(impl: LocalGeneratedMapObjectAreaSource): WatchtowerDefinitionTileSource
fun bindWatchtowerRepository(impl: DeterministicWatchtowerRepository): WatchtowerRepository
```

- [x] **Step 5: Delete watchtower files and tests**

Delete watchtower files and tests from the File Structure Map.

- [x] **Step 6: Run compile**

Run: `./gradlew :app:compileDebugKotlin -q`

Expected: FAIL only on database/map-object references removed in Task 1.3 and character/player-stat references removed in Task 1.4.

- [x] **Step 7: Commit**

Run:

```bash
git add app/src/main/java/com/github/arhor/journey app/src/test/java/com/github/arhor/journey app/src/androidTest/java/com/github/arhor/journey app/src/main/res/values/strings.xml
git commit -m "refactor: remove watchtower gameplay surface"
```

### Task 1.3: Remove watchtower and character/player-stat tables

**Files:**

- Modify: `app/src/main/java/com/github/arhor/journey/data/local/db/JourneyDatabase.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/data/di/DatabaseModule.kt`
- Modify: `app/src/androidTest/java/com/github/arhor/journey/data/local/db/JourneyDatabaseMigrationTest.kt`
- Delete: old DAO/entity files listed in the File Structure Map.

- [x] **Step 1: Write migration test for schema cleanup**

Add a migration test:

```kotlin
@Test
fun migrate5To6_should_drop_old_gameplay_foundation_tables() {
    val dbName = "journey-foundation-cleanup-migration-test"

    migrationHelper.createDatabase(dbName, 5).apply {
        execSQL("CREATE TABLE IF NOT EXISTS `hero` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `level` INTEGER NOT NULL, `xpInLevel` INTEGER NOT NULL, `energyNow` INTEGER NOT NULL, `energyMax` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        execSQL("CREATE TABLE IF NOT EXISTS `hero_resources` (`heroId` TEXT NOT NULL, `typeId` TEXT NOT NULL, `amount` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`heroId`, `typeId`))")
        execSQL("CREATE TABLE IF NOT EXISTS `collected_resource_spawns` (`heroId` TEXT NOT NULL, `typeId` TEXT NOT NULL, `spawnId` TEXT NOT NULL, `collectedAt` INTEGER NOT NULL, PRIMARY KEY(`heroId`, `spawnId`))")
        execSQL("CREATE TABLE IF NOT EXISTS `watchtower_state` (`watchtowerId` TEXT NOT NULL, `discoveredAt` INTEGER NOT NULL, `claimedAt` INTEGER, `level` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`watchtowerId`))")
        close()
    }

    val migrated = migrationHelper.runMigrationsAndValidate(
        dbName,
        6,
        true,
        JourneyDatabase.Companion.MIGRATION_5_6,
    )

    migrated.hasTable("hero").shouldBeFalse()
    migrated.hasTable("hero_resources").shouldBeFalse()
    migrated.hasTable("collected_resource_spawns").shouldBeFalse()
    migrated.hasTable("watchtower_state").shouldBeFalse()
    migrated.hasTable("breach_node_state").shouldBeTrue()
    migrated.close()

    val context = InstrumentationRegistry.getInstrumentation().targetContext
    context.deleteDatabase(dbName) shouldBe true
}
```

- [x] **Step 2: Run migration test and verify failure**

Run: `./gradlew :app:assembleDebugAndroidTest`

Expected: FAIL because `MIGRATION_5_6` and `breach_node_state` do not exist.

- [x] **Step 3: Update `JourneyDatabase`**

Set database version to `6`, remove old entities, and add migration:

```kotlin
@Database(
    entities = [
        ExploredTileEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
```

Use this migration body for the cleanup stage:

```kotlin
val MIGRATION_5_6 = Migration(5, 6) { db ->
    db.execSQL("DROP TABLE IF EXISTS `watchtower_state`")
    db.execSQL("DROP TABLE IF EXISTS `hero_resources`")
    db.execSQL("DROP TABLE IF EXISTS `collected_resource_spawns`")
    db.execSQL("DROP TABLE IF EXISTS `hero`")
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `breach_node_state` (
            `breachNodeId` TEXT NOT NULL,
            `h3CellId` TEXT NOT NULL,
            `discoveredAt` INTEGER,
            `controlledAt` INTEGER,
            `lockdownUntil` INTEGER,
            `updatedAt` INTEGER NOT NULL,
            PRIMARY KEY(`breachNodeId`)
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE UNIQUE INDEX IF NOT EXISTS `index_breach_node_state_h3CellId`
        ON `breach_node_state` (`h3CellId`)
        """.trimIndent(),
    )
}
```

Append it to `MIGRATIONS`.

- [x] **Step 4: Update `DatabaseModule`**

Keep only:

```kotlin
@Provides
@Singleton
fun provideExplorationTileDao(db: JourneyDatabase): ExplorationTileDao = db.explorationTileDao()

@Provides
@Singleton
fun provideTransactionRunner(db: JourneyDatabase): TransactionRunner = RoomTransactionRunner(db)
```

Ticket 2 adds `BreachNodeStateDao`.

- [x] **Step 5: Run validation**

Run: `./gradlew :app:assembleDebugAndroidTest`

Expected: PASS for Android test source compilation. Device execution is not required for this compile gate.

- [x] **Step 6: Commit**

Run:

```bash
git add app/src/main/java/com/github/arhor/journey/data app/src/androidTest/java/com/github/arhor/journey/data/local/db
git commit -m "refactor: remove old gameplay persistence"
```

### Task 1.4: Remove player inventory and old resource collection dependencies

**Files:**

- Delete: `app/src/main/java/com/github/arhor/journey/domain/model/HeroResource.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/domain/model/CollectedResourceSpawn.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/domain/model/CollectedResourceSpawnReward.kt`
- Delete: player inventory and collected-resource use cases listed in the File Structure Map.
- Delete: related repository and mapper tests.
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapViewModel.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/presentation/MapWorldObjectPresenter.kt`

- [x] **Step 1: Remove resource collection dependencies from map state**

Remove constructor dependency:

```kotlin
private val observeCollectibleResourceSpawns: ObserveCollectibleResourceSpawnsUseCase,
```

Set visible world objects to empty until breach markers are introduced:

```kotlin
private fun observeVisibleWorldObjects(): Flow<Output<VisibleWorldObjects, DomainError>> =
    flowOf(Output.Success(VisibleWorldObjects()))
```

- [x] **Step 2: Delete old inventory and resource collection files**

Delete the old player inventory, collected-spawn, and player-resource files. Keep `ResourceType.kt` only if another remaining file imports it after compile cleanup.

- [x] **Step 3: Run repository-wide reference check**

Run: `rg -n "Hero|hero|Energy|energy|Watchtower|watchtower|HeroResource|CollectedResource" app/src/main/java app/src/test/java app/src/androidTest/java`

Expected: no output except historical migration SQL strings inside migration tests when those strings are required to create legacy schemas.

- [x] **Step 4: Run compile**

Run: `./gradlew :app:compileDebugKotlin -q`

Expected: PASS.

- [x] **Step 5: Commit**

Run:

```bash
git add app/src/main/java app/src/test/java app/src/androidTest/java app/src/main/res/values/strings.xml
git commit -m "refactor: remove old player inventory foundation"
```

## Ticket 2: H3 Spatial Adapter And Breach Persistence

### Task 2.1: Add H3 adapter boundary

**Files:**

- Create: `app/src/main/java/com/github/arhor/journey/domain/spatial/H3Grid.kt`
- Create: `app/src/main/java/com/github/arhor/journey/data/spatial/UberH3Grid.kt`
- Create: `app/src/main/java/com/github/arhor/journey/data/di/SpatialModule.kt`
- Create: `app/src/test/java/com/github/arhor/journey/core/testing/FakeH3Grid.kt`
- Test: `app/src/test/java/com/github/arhor/journey/data/spatial/UberH3GridTest.kt`

- [x] **Step 1: Write adapter contract test**

Create `UberH3GridTest.kt`:

```kotlin
class UberH3GridTest {

    @Test
    fun `cellId should return stable address when location and resolution are fixed`() {
        // Given
        val subject = UberH3Grid()

        // When
        val actual = subject.cellId(lat = 50.4501, lon = 30.5234, resolution = 9)

        // Then
        actual shouldBe subject.cellId(lat = 50.4501, lon = 30.5234, resolution = 9)
    }

    @Test
    fun `gridDisk should include origin cell when radius is zero`() {
        // Given
        val subject = UberH3Grid()
        val origin = subject.cellId(lat = 50.4501, lon = 30.5234, resolution = 9)

        // When
        val actual = subject.gridDisk(origin, radius = 0)

        // Then
        actual shouldBe listOf(origin)
    }
}
```

- [x] **Step 2: Run test and verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.data.spatial.UberH3GridTest"`

Expected: FAIL because `UberH3Grid` does not exist.

- [x] **Step 3: Implement adapter**

Create `H3Grid.kt`:

```kotlin
package com.github.arhor.journey.domain.spatial

import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint

interface H3Grid {
    fun cellId(lat: Double, lon: Double, resolution: Int): String
    fun cellCenter(cellId: String): GeoPoint
    fun cellBoundary(cellId: String): List<GeoPoint>
    fun gridDisk(cellId: String, radius: Int): List<String>
    fun gridDistance(originCellId: String, destinationCellId: String): Long
    fun averageEdgeLengthMeters(resolution: Int): Double
    fun cellsInBounds(bounds: GeoBounds, resolution: Int): List<String>
}
```

Create `UberH3Grid.kt`:

```kotlin
package com.github.arhor.journey.data.spatial

import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.spatial.H3Grid
import com.uber.h3core.H3Core
import com.uber.h3core.LengthUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

@Singleton
class UberH3Grid @Inject constructor() : H3Grid {
    private val h3 = H3Core.newInstance()

    override fun cellId(lat: Double, lon: Double, resolution: Int): String =
        h3.latLngToCellAddress(lat, lon, resolution)

    override fun cellCenter(cellId: String): GeoPoint {
        val center = h3.cellToLatLng(cellId)
        return GeoPoint(lat = center.lat, lon = center.lng)
    }

    override fun cellBoundary(cellId: String): List<GeoPoint> =
        h3.cellToBoundary(cellId).map { point ->
            GeoPoint(lat = point.lat, lon = point.lng)
        }

    override fun gridDisk(cellId: String, radius: Int): List<String> =
        h3.gridDisk(cellId, radius.coerceAtLeast(0))

    override fun gridDistance(originCellId: String, destinationCellId: String): Long =
        h3.gridDistance(originCellId, destinationCellId)

    override fun averageEdgeLengthMeters(resolution: Int): Double =
        h3.getHexagonEdgeLengthAvg(resolution, LengthUnit.m)

    override fun cellsInBounds(bounds: GeoBounds, resolution: Int): List<String> {
        val center = GeoPoint(
            lat = (bounds.south + bounds.north) / 2.0,
            lon = (bounds.west + bounds.east) / 2.0,
        )
        val centerCell = cellId(center.lat, center.lon, resolution)
        val edgeLengthMeters = averageEdgeLengthMeters(resolution)
        val diagonalMeters = maxOf(
            center.distanceTo(GeoPoint(lat = bounds.north, lon = bounds.east)),
            center.distanceTo(GeoPoint(lat = bounds.north, lon = bounds.west)),
            center.distanceTo(GeoPoint(lat = bounds.south, lon = bounds.east)),
            center.distanceTo(GeoPoint(lat = bounds.south, lon = bounds.west)),
        )
        val radius = ceil(diagonalMeters / edgeLengthMeters).toInt().coerceAtLeast(1)
        return gridDisk(centerCell, radius)
            .filter { cellId -> bounds.contains(cellCenter(cellId)) }
            .distinct()
            .sorted()
    }
}
```

Create `SpatialModule.kt`:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
interface SpatialModule {
    @Binds
    fun bindH3Grid(impl: UberH3Grid): H3Grid
}
```

Create shared test helper `FakeH3Grid.kt`:

```kotlin
package com.github.arhor.journey.core.testing

import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.spatial.H3Grid
import kotlin.math.abs

class FakeH3Grid(
    private val originCell: String = "origin",
    private val disk: List<String> = listOf(originCell),
    private val centers: Map<String, GeoPoint> = emptyMap(),
    private val boundaries: Map<String, List<GeoPoint>> = emptyMap(),
    private val cellsInBounds: List<String> = disk,
    private val averageEdgeLengthMeters: Double = 180.0,
) : H3Grid {
    override fun cellId(lat: Double, lon: Double, resolution: Int): String = originCell

    override fun cellCenter(cellId: String): GeoPoint =
        centers[cellId] ?: GeoPoint(lat = 50.45 + (abs(cellId.hashCode()) % 100) * 0.00001, lon = 30.52)

    override fun cellBoundary(cellId: String): List<GeoPoint> =
        boundaries[cellId] ?: hexAround(cellCenter(cellId))

    override fun gridDisk(cellId: String, radius: Int): List<String> = disk

    override fun gridDistance(originCellId: String, destinationCellId: String): Long =
        disk.indexOf(destinationCellId).takeIf { it >= 0 }?.toLong() ?: Long.MAX_VALUE

    override fun averageEdgeLengthMeters(resolution: Int): Double = averageEdgeLengthMeters

    override fun cellsInBounds(bounds: GeoBounds, resolution: Int): List<String> = cellsInBounds

    companion object {
        fun withRepeatedCells(): FakeH3Grid =
            FakeH3Grid(
                disk = (0..200).map { index -> "cell-$index" },
                cellsInBounds = (0..200).map { index -> "cell-$index" },
            )
    }
}

fun hexAround(center: GeoPoint): List<GeoPoint> =
    listOf(
        GeoPoint(center.lat + 0.001, center.lon),
        GeoPoint(center.lat + 0.0005, center.lon + 0.001),
        GeoPoint(center.lat - 0.0005, center.lon + 0.001),
        GeoPoint(center.lat - 0.001, center.lon),
        GeoPoint(center.lat - 0.0005, center.lon - 0.001),
        GeoPoint(center.lat + 0.0005, center.lon - 0.001),
    )
```

- [x] **Step 4: Run adapter test**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.data.spatial.UberH3GridTest"`

Expected: PASS.

- [x] **Step 5: Commit**

Run:

```bash
git add app/src/main/java/com/github/arhor/journey/domain/spatial app/src/main/java/com/github/arhor/journey/data/spatial app/src/main/java/com/github/arhor/journey/data/di/SpatialModule.kt app/src/test/java/com/github/arhor/journey/core/testing/FakeH3Grid.kt app/src/test/java/com/github/arhor/journey/data/spatial
git commit -m "feat: add H3 spatial adapter"
```

### Task 2.2: Add breach domain models and deterministic generation

**Files:**

- Create breach domain model files listed in File Structure Map.
- Create: `app/src/main/java/com/github/arhor/journey/domain/internal/BreachNodeGeneration.kt`
- Test: `app/src/test/java/com/github/arhor/journey/domain/internal/BreachNodeGenerationTest.kt`

- [x] **Step 1: Write generation tests**

Create tests:

```kotlin
class BreachNodeGenerationTest {

    @Test
    fun `definitionForCell should return stable definition when cell is occupied`() {
        // Given
        val h3 = FakeH3Grid.withRepeatedCells()
        val cellId = firstOccupiedCell(h3)

        // When
        val first = BreachNodeGeneration.definitionForCell(cellId, h3)
        val second = BreachNodeGeneration.definitionForCell(cellId, h3)

        // Then
        first shouldBe second
    }

    @Test
    fun `definitionForCell should use h3 cell id in stable breach id`() {
        // Given
        val h3 = FakeH3Grid.withRepeatedCells()
        val cellId = firstOccupiedCell(h3)

        // When
        val actual = BreachNodeGeneration.definitionForCell(cellId, h3)

        // Then
        actual?.id shouldBe "breach-node:v1:h3r9:$cellId"
    }

    private fun firstOccupiedCell(h3: H3Grid): String =
        generateSequence(0) { index -> index + 1 }
            .map { index -> "cell-$index" }
            .first { cellId -> BreachNodeGeneration.definitionForCell(cellId, h3) != null }
}
```

- [x] **Step 2: Run tests and verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.domain.internal.BreachNodeGenerationTest"`

Expected: FAIL because breach generation does not exist.

- [x] **Step 3: Add models**

Create `BreachNodeDefinition.kt`:

```kotlin
data class BreachNodeDefinition(
    val id: String,
    val h3CellId: String,
    val districtName: String,
    val description: String?,
    val location: GeoPoint,
    val interactionRadiusMeters: Double,
    val controlledH3CellIds: Set<String>,
)
```

Create `BreachNodeState.kt`, `BreachNodeRecord.kt`, `BreachNodePhase.kt`, `BreachNode.kt`, and `ControlledBreachRevealSnapshot.kt` with fields from the spec.

- [x] **Step 4: Implement generation**

Create `BreachBalance.kt`:

```kotlin
internal object BreachBalance {
    const val GENERATOR_VERSION = 1
    const val H3_RESOLUTION = 9
    const val OCCUPANCY_THRESHOLD_PERCENT = 34
    const val INTERACTION_RADIUS_METERS = 35.0
}
```

Create `BreachNodeGeneration.kt`:

```kotlin
object BreachNodeGeneration {
    fun definitionForCell(cellId: String, h3Grid: H3Grid): BreachNodeDefinition? {
        if (!isOccupied(cellId)) return null
        return BreachNodeDefinition(
            id = "breach-node:v${BreachBalance.GENERATOR_VERSION}:h3r${BreachBalance.H3_RESOLUTION}:$cellId",
            h3CellId = cellId,
            districtName = buildDistrictName(cellId),
            description = "A vulnerable infrastructure node bleeding encrypted signal noise.",
            location = h3Grid.cellCenter(cellId),
            interactionRadiusMeters = BreachBalance.INTERACTION_RADIUS_METERS,
            controlledH3CellIds = setOf(cellId),
        )
    }

    fun definitionsForCells(cellIds: Collection<String>, h3Grid: H3Grid): List<BreachNodeDefinition> =
        cellIds.mapNotNull { cellId -> definitionForCell(cellId, h3Grid) }.sortedBy { it.id }

    private fun isOccupied(cellId: String): Boolean =
        stablePositiveHash("breach-node:v${BreachBalance.GENERATOR_VERSION}:occupied:$cellId") % 100 <
            BreachBalance.OCCUPANCY_THRESHOLD_PERCENT

    private fun buildDistrictName(cellId: String): String =
        "Sector ${stablePositiveHash("breach-node:v${BreachBalance.GENERATOR_VERSION}:name:$cellId") % 10_000}"

    private fun stablePositiveHash(seed: String): Int =
        seed.hashCode() and 0x7fffffff
}
```

- [x] **Step 5: Run generation tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.domain.internal.BreachNodeGenerationTest"`

Expected: PASS.

- [x] **Step 6: Commit**

Run:

```bash
git add app/src/main/java/com/github/arhor/journey/domain app/src/test/java/com/github/arhor/journey/domain/internal
git commit -m "feat: add breach node generation"
```

### Task 2.3: Add breach state persistence and repository

**Files:**

- Create: `app/src/main/java/com/github/arhor/journey/data/local/db/entity/BreachNodeStateEntity.kt`
- Create: `app/src/main/java/com/github/arhor/journey/data/local/db/dao/BreachNodeStateDao.kt`
- Create: `app/src/main/java/com/github/arhor/journey/data/mapper/BreachNodeMapper.kt`
- Create: `app/src/main/java/com/github/arhor/journey/domain/repository/BreachNodeRepository.kt`
- Create: `app/src/main/java/com/github/arhor/journey/data/repository/DeterministicBreachNodeRepository.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/data/local/db/JourneyDatabase.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/data/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/data/di/RepositoryModule.kt`
- Test: `app/src/test/java/com/github/arhor/journey/data/repository/DeterministicBreachNodeRepositoryTest.kt`

- [x] **Step 1: Write repository tests**

Create tests that use `FakeH3Grid` and an in-memory fake DAO:

```kotlin
@Test
fun `getInBounds should compose generated definitions with persisted states`() = runTest {
    // Given
    val h3Grid = FakeH3Grid(
        cellsInBounds = listOf("cell-a"),
        centers = mapOf("cell-a" to GeoPoint(lat = 50.45, lon = 30.52)),
        boundaries = mapOf("cell-a" to hexAround(50.45, 30.52)),
    )
    val dao = FakeBreachNodeStateDao(
        states = listOf(breachNodeStateEntity("breach-node:v1:h3r9:cell-a", "cell-a")),
    )
    val subject = DeterministicBreachNodeRepository(dao, h3Grid)

    // When
    val actual = subject.getInBounds(boundsAround(50.45, 30.52))

    // Then
    actual.single().state?.h3CellId shouldBe "cell-a"
}
```

- [x] **Step 2: Run repository test and verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.data.repository.DeterministicBreachNodeRepositoryTest"`

Expected: FAIL because repository files do not exist.

- [x] **Step 3: Add DAO/entity and database wiring**

Create entity:

```kotlin
@Entity(
    tableName = "breach_node_state",
    indices = [Index(value = ["h3CellId"], unique = true)],
)
data class BreachNodeStateEntity(
    @PrimaryKey val breachNodeId: String,
    val h3CellId: String,
    val discoveredAt: Instant?,
    val controlledAt: Instant?,
    val lockdownUntil: Instant?,
    val updatedAt: Instant,
)
```

Create DAO with `observeByCellIds`, `getByCellIds`, `getById`, `getByH3CellId`, `upsert`, and `markControlled`.

- [x] **Step 4: Add repository contract and implementation**

Repository contract:

```kotlin
interface BreachNodeRepository {
    fun observeInBounds(bounds: GeoBounds): Flow<List<BreachNodeRecord>>
    suspend fun getInBounds(bounds: GeoBounds): List<BreachNodeRecord>
    suspend fun getForCells(h3CellIds: Collection<String>): List<BreachNodeRecord>
    suspend fun getById(id: String): BreachNodeRecord?
    suspend fun getByH3CellId(h3CellId: String): BreachNodeRecord?
    suspend fun upsertDiscovered(id: String, h3CellId: String, discoveredAt: Instant, updatedAt: Instant): Boolean
    suspend fun markControlled(id: String, h3CellId: String, controlledAt: Instant, updatedAt: Instant): Boolean
    fun observeControlledCells(bounds: GeoBounds): Flow<Set<String>>
}
```

Implementation composes `BreachNodeGeneration.definitionsForCells(...)` with DAO state rows.

- [x] **Step 5: Bind repository**

Add to `RepositoryModule.kt`:

```kotlin
@Binds
fun bindBreachNodeRepository(impl: DeterministicBreachNodeRepository): BreachNodeRepository
```

Add `provideBreachNodeStateDao` to `DatabaseModule.kt`.

- [x] **Step 6: Run tests and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.data.repository.DeterministicBreachNodeRepositoryTest"
./gradlew :app:compileDebugKotlin -q
```

Expected: PASS.

- [x] **Step 7: Commit**

Run:

```bash
git add app/src/main/java/com/github/arhor/journey/data app/src/main/java/com/github/arhor/journey/domain app/src/test/java/com/github/arhor/journey/data/repository
git commit -m "feat: add breach node persistence"
```

## Ticket 3: Breach Rules Use Cases

### Task 3.1: Add scan use case

**Files:**

- Create: `app/src/main/java/com/github/arhor/journey/domain/usecase/FindNearestBreachNodeUseCase.kt`
- Create: `app/src/main/java/com/github/arhor/journey/domain/model/error/BreachNodeError.kt`
- Test: `app/src/test/java/com/github/arhor/journey/domain/usecase/FindNearestBreachNodeUseCaseTest.kt`

- [x] **Step 1: Write nearest scan tests**

Tests must cover: nearest uncontrolled result, no candidates, controlled candidates filtered, lockdown candidates filtered.

```kotlin
@Test
fun `invoke should return nearest uncontrolled breach when candidates exist in scan disk`() = runTest {
    // Given
    val actor = GeoPoint(lat = 50.45, lon = 30.52)
    val repository = FakeBreachNodeRepository(records = listOf(uncontrolledRecord("cell-near", actor)))
    val h3Grid = FakeH3Grid(originCell = "origin", disk = listOf("origin", "cell-near"))
    val subject = FindNearestBreachNodeUseCase(repository, h3Grid)

    // When
    val actual = subject(actor)

    // Then
    (actual as Output.Success).value.definition.h3CellId shouldBe "cell-near"
}
```

- [x] **Step 2: Run and verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.domain.usecase.FindNearestBreachNodeUseCaseTest"`

Expected: FAIL because use case does not exist.

- [x] **Step 3: Implement use case**

Use `H3Grid.cellId`, `H3Grid.gridDisk`, and repository `getForCells`. Scan range starts with `1_000.0` meters. H3 disk radius is:

```kotlin
val ringRadius = ceil(BreachBalance.SCAN_RANGE_METERS / h3Grid.averageEdgeLengthMeters(BreachBalance.H3_RESOLUTION))
    .toInt()
    .coerceAtLeast(1)
```

Return `Output.Failure(BreachNodeError.NotFound)` when there is no candidate.

- [x] **Step 4: Run scan tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.domain.usecase.FindNearestBreachNodeUseCaseTest"`

Expected: PASS.

- [x] **Step 5: Commit**

Run:

```bash
git add app/src/main/java/com/github/arhor/journey/domain app/src/test/java/com/github/arhor/journey/domain/usecase
git commit -m "feat: add breach scan rule"
```

### Task 3.2: Add discovery, completion, and controlled reveal use cases

**Files:**

- Create: `DiscoverBreachNodeUseCase.kt`
- Create: `CompleteBreachUseCase.kt`
- Create: `ObserveControlledBreachRevealCellsUseCase.kt`
- Create: `ObserveVisibleBreachNodesUseCase.kt`
- Test: `app/src/test/java/com/github/arhor/journey/domain/usecase/BreachNodeUseCaseTest.kt`

- [x] **Step 1: Write rule tests**

Cover:

- discovery persists when actor is within interaction radius;
- discovery returns out-of-range failure outside radius;
- completion marks controlled only in range;
- controlled reveal emits controlled H3 cells in bounds;
- visible breaches include discovered and controlled nodes.

- [x] **Step 2: Run tests and verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.domain.usecase.BreachNodeUseCaseTest"`

Expected: FAIL because use cases do not exist.

- [x] **Step 3: Implement use cases**

`CompleteBreachUseCase` signature:

```kotlin
suspend operator fun invoke(
    id: String,
    actorLocation: GeoPoint,
): Output<BreachNodeState, BreachNodeError>
```

Validation:

```kotlin
val record = repository.getById(id) ?: return Output.Failure(BreachNodeError.NotFound(id))
val distanceMeters = actorLocation.distanceTo(record.definition.location)
if (distanceMeters > record.definition.interactionRadiusMeters) {
    return Output.Failure(BreachNodeError.NotInRange(id, distanceMeters, record.definition.interactionRadiusMeters))
}
```

- [x] **Step 4: Run use case tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.domain.usecase.BreachNodeUseCaseTest"`

Expected: PASS.

- [x] **Step 5: Commit**

Run:

```bash
git add app/src/main/java/com/github/arhor/journey/domain app/src/test/java/com/github/arhor/journey/domain/usecase
git commit -m "feat: add breach completion rules"
```

## Ticket 4: Map ViewModel Session Flow

### Task 4.1: Add breach protocol UI state and intents

**Files:**

- Create: `app/src/main/java/com/github/arhor/journey/feature/map/BreachProtocolUiState.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapUiState.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapIntent.kt`
- Test: `app/src/test/java/com/github/arhor/journey/feature/map/MapViewModelTest.kt`

- [x] **Step 1: Add failing ViewModel test for idle state**

```kotlin
@Test
fun `uiState should expose idle breach protocol state before pulse`() = runTest {
    // Given
    val fixture = createFixture()

    // When
    val actual = fixture.viewModel.awaitContent()

    // Then
    actual.breachProtocol shouldBe BreachProtocolUiState.Idle
}
```

- [x] **Step 2: Run test and verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.MapViewModelTest"`

Expected: FAIL because `breachProtocol` does not exist.

- [x] **Step 3: Add UI state**

Create:

```kotlin
sealed interface BreachProtocolUiState {
    data object Idle : BreachProtocolUiState
    data object Scanning : BreachProtocolUiState
    data class SignalLocked(
        val breachNodeId: String,
        val districtName: String,
        val distanceMeters: Int?,
        val signalStrengthPercent: Int,
        val canStartUpload: Boolean,
        val disabledReason: String?,
    ) : BreachProtocolUiState
    data class Uploading(
        val breachNodeId: String,
        val districtName: String,
        val progressPercent: Int,
    ) : BreachProtocolUiState
    data class Completed(
        val districtName: String,
    ) : BreachProtocolUiState
}
```

Add `val breachProtocol: BreachProtocolUiState` to `MapUiState.Content`.

- [x] **Step 4: Add intents**

Add:

```kotlin
data object PulseClicked : MapIntent
data object StartBreachUpload : MapIntent
data object BreachUploadTick : MapIntent
data object CancelBreachUpload : MapIntent
data object DismissBreachPanel : MapIntent
```

- [x] **Step 5: Run test**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.MapViewModelTest"`

Expected: PASS for the new idle-state test after fixture updates.

- [x] **Step 6: Commit**

Run:

```bash
git add app/src/main/java/com/github/arhor/journey/feature/map app/src/test/java/com/github/arhor/journey/feature/map/MapViewModelTest.kt
git commit -m "feat: expose breach protocol map state"
```

### Task 4.2: Wire pulse scan, signal strength, upload, and completion

**Files:**

- Modify: `MapViewModel.kt`
- Modify: `MapViewModelTest.kt`

- [x] **Step 1: Add failing pulse test**

Test that `PulseClicked` calls `FindNearestBreachNodeUseCase` with current location and moves to `SignalLocked`.

- [x] **Step 2: Add failing upload completion test**

Test that `StartBreachUpload`, repeated `BreachUploadTick`, and completion call `CompleteBreachUseCase`.

- [x] **Step 3: Implement session state**

Add private state:

```kotlin
val lockedBreach: BreachNode? = null,
val uploadProgressPercent: Int = 0,
val breachPhase: BreachSessionPhase = BreachSessionPhase.IDLE,
```

Use distance-to-target to derive signal strength:

```kotlin
private fun signalStrengthPercent(distanceMeters: Double?): Int =
    distanceMeters
        ?.let { distance -> (100 - ((distance / BREACH_SCAN_RANGE_METERS) * 100)).toInt().coerceIn(0, 100) }
        ?: 0
```

- [x] **Step 4: Implement handlers**

Handlers:

- `onPulseClicked`
- `onStartBreachUpload`
- `onBreachUploadTick`
- `onCancelBreachUpload`
- `onDismissBreachPanel`

Use `CompleteBreachUseCase` when progress reaches 100.

- [x] **Step 5: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.MapViewModelTest"`

Expected: PASS.

- [x] **Step 6: Commit**

Run:

```bash
git add app/src/main/java/com/github/arhor/journey/feature/map/MapViewModel.kt app/src/test/java/com/github/arhor/journey/feature/map/MapViewModelTest.kt
git commit -m "feat: add breach map session flow"
```

## Ticket 5: Breach Compose Overlays

### Task 5.1: Add Pulse and signal/upload overlays

**Files:**

- Create: `app/src/main/java/com/github/arhor/journey/feature/map/BreachProtocolOverlay.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/java/com/github/arhor/journey/feature/map/MapScreenTest.kt`

- [x] **Step 1: Add Compose tests**

Add tests for:

- idle state shows Pulse button;
- signal locked state shows signal strength text;
- upload state shows progress text;
- tapping Pulse dispatches `MapIntent.PulseClicked`.

- [x] **Step 2: Run Android test compile**

Run: `./gradlew :app:assembleDebugAndroidTest`

Expected: FAIL because overlay does not exist.

- [x] **Step 3: Implement overlay**

Create `BreachProtocolOverlay`:

```kotlin
@Composable
internal fun BreachProtocolOverlay(
    state: BreachProtocolUiState,
    dispatch: (MapIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            BreachProtocolUiState.Idle -> Button(
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                onClick = { dispatch(MapIntent.PulseClicked) },
            ) {
                Text(text = stringResource(R.string.breach_pulse_button))
            }
            BreachProtocolUiState.Scanning -> TextPanel(text = stringResource(R.string.breach_scanning))
            is BreachProtocolUiState.SignalLocked -> SignalPanel(state = state, dispatch = dispatch)
            is BreachProtocolUiState.Uploading -> UploadPanel(state = state)
            is BreachProtocolUiState.Completed -> TextPanel(text = stringResource(R.string.breach_complete_message, state.districtName))
        }
    }
}
```

Wire it in `MapContent` above the startup splash.

- [x] **Step 4: Run Android test compile**

Run: `./gradlew :app:assembleDebugAndroidTest`

Expected: PASS.

- [x] **Step 5: Commit**

Run:

```bash
git add app/src/main/java/com/github/arhor/journey/feature/map app/src/main/res/values/strings.xml app/src/androidTest/java/com/github/arhor/journey/feature/map/MapScreenTest.kt
git commit -m "feat: add breach protocol overlays"
```

## Ticket 6: Dynamic Breach Map Object Rendering

### Task 6.1: Add breach object presentation

**Files:**

- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/model/MapObjectUiModel.kt`
- Create: `app/src/main/java/com/github/arhor/journey/feature/map/model/BreachMarkerState.kt`
- Create: `app/src/main/java/com/github/arhor/journey/feature/map/presentation/BreachNodePresenter.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapViewModel.kt`
- Test: `app/src/test/java/com/github/arhor/journey/feature/map/presentation/BreachNodePresenterTest.kt`

- [x] **Step 1: Write presenter tests**

Test discovered, upload-ready, controlled, and lockdown marker states.

- [x] **Step 2: Implement presenter**

Add `MapObjectKind.BreachNode(idPrefix = "breach")`. Create `BreachNodePresenter.present(...)` returning `MapObjectUiModel`.

- [x] **Step 3: Update ViewModel visible objects**

Combine `ObserveVisibleBreachNodesUseCase` with breach presenter. Keep resource objects absent.

- [x] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.presentation.BreachNodePresenterTest"`

Expected: PASS.

- [x] **Step 5: Commit**

Run:

```bash
git add app/src/main/java/com/github/arhor/journey/feature/map app/src/test/java/com/github/arhor/journey/feature/map/presentation
git commit -m "feat: present breach map objects"
```

### Task 6.2: Render breach markers with MapLibre GeoJSON layers

**Files:**

- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/MapLibreViewMapScreen.kt`
- Create: `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/MapObjectLayerController.kt`
- Test: `app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/MapObjectLayerControllerTest.kt`

- [x] **Step 1: Add rendering path decision**

Use a MapLibre GeoJSON source with circle layers. This avoids a screen-projection bridge and keeps marker hit-testing in
MapLibre.

- [x] **Step 2: Add test for object list diffing**

Test that the controller converts map objects to GeoJSON features sorted by id and keeps object ids in feature
properties.

- [x] **Step 3: Implement `MapObjectLayerController`**

Controller responsibilities:

```kotlin
internal class MapObjectLayerController {
    fun attach(style: Style)
    fun update(objects: List<MapObjectUiModel>)
    fun queryObjectIdAt(map: MapLibreMap, screenPoint: PointF): String?
    fun cleanup()
}
```

Use one `GeoJsonSource` named `journey-map-objects-source` and one `CircleLayer` named
`journey-breach-node-circle-layer`. Each feature must include an `objectId` string property.

- [x] **Step 4: Pass objects through map screen**

Add `visibleObjects` parameter to `MapLibreViewMapScreen`:

```kotlin
visibleObjects: List<MapObjectUiModel> = emptyList(),
onObjectTapped: (String) -> Unit = {},
```

Wire from `MapScreen`.

- [x] **Step 5: Implement marker tap dispatch**

Marker tap must call:

```kotlin
dispatch(MapIntent.BreachNodeTapped(objectId))
```

- [x] **Step 6: Run compile and focused tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.MapObjectLayerControllerTest"
./gradlew :app:compileDebugKotlin -q
```

Expected: PASS.

- [x] **Step 7: Commit**

Run:

```bash
git add app/src/main/java/com/github/arhor/journey/feature/map app/src/test/java/com/github/arhor/journey/feature/map/viewinterop
git commit -m "feat: render breach map markers"
```

## Ticket 7: Visible Controlled-Sector Fog Reveal

### Task 7.1: Convert controlled H3 cells to fog visibility

**Files:**

- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/fow/FogOfWarController.kt`
- Create: `app/src/main/java/com/github/arhor/journey/feature/map/fow/H3FogRevealMapper.kt`
- Test: `app/src/test/java/com/github/arhor/journey/feature/map/fow/H3FogRevealMapperTest.kt`
- Test: `app/src/test/java/com/github/arhor/journey/feature/map/fow/FogOfWarControllerTest.kt`

- [ ] **Step 1: Write mapper test**

Test that one H3 cell boundary maps to a non-empty set of canonical fog tiles by checking tile and cell boundary
intersection.

- [ ] **Step 2: Run mapper test and verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.fow.H3FogRevealMapperTest"`

Expected: FAIL because mapper does not exist.

- [ ] **Step 3: Implement mapper**

Inputs:

```kotlin
fun revealTilesForCells(
    h3CellIds: Set<String>,
    canonicalZoom: Int,
): Set<MapTile>
```

Use `H3Grid.cellBoundary(cellId)` to build bounds, then reuse existing tile range and geometry intersection helpers where possible.

- [ ] **Step 4: Replace watchtower reveal in controller**

Inject `ObserveControlledBreachRevealCellsUseCase` and `H3FogRevealMapper`. Convert controlled cells to persistent tile mask inside `FogOfWarController`.

- [ ] **Step 5: Run fog tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.fow.FogOfWarControllerTest"`

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add app/src/main/java/com/github/arhor/journey/feature/map/fow app/src/test/java/com/github/arhor/journey/feature/map/fow
git commit -m "feat: reveal fog from controlled breach cells"
```

### Task 7.2: Reconnect native fog layer rendering

**Files:**

- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/MapLibreViewMapScreen.kt`
- Test: `app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeFogOfWarLayerControllerTest.kt`

- [ ] **Step 1: Add regression test for fog state delivery**

Test that `MapLibreViewMapScreen` forwards `FogOfWarRenderState` to `NativeFogOfWarLayerController.update`.

- [ ] **Step 2: Re-enable fog controller lines**

Restore the commented controller creation, attach, update, and cleanup path:

```kotlin
val fogLayerController = NativeFogOfWarLayerController()
fogLayerController.attach(style)
fogLayerController.update(fogOfWar)
handle.fogLayerController.update(fogOfWar)
```

- [ ] **Step 3: Run compile and native fog tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeFogOfWarLayerControllerTest"
./gradlew :app:compileDebugKotlin -q
```

Expected: PASS.

- [ ] **Step 4: Commit**

Run:

```bash
git add app/src/main/java/com/github/arhor/journey/feature/map/viewinterop app/src/test/java/com/github/arhor/journey/feature/map/viewinterop
git commit -m "feat: render controlled breach fog reveal"
```

## Ticket 8: Epic Validation And Handoff

### Task 8.1: Run final focused verification

**Files:**

- Modify: none unless failures reveal a real defect.

- [ ] **Step 1: Run JVM tests**

Run: `./gradlew :app:testDebugUnitTest`

Expected: PASS.

- [ ] **Step 2: Compile Kotlin**

Run: `./gradlew :app:compileDebugKotlin -q`

Expected: PASS.

- [ ] **Step 3: Assemble Android tests**

Run: `./gradlew :app:assembleDebugAndroidTest`

Expected: PASS.

- [ ] **Step 4: Search for removed concepts**

Run:

```bash
rg -n "Hero|hero|Energy|energy|Watchtower|watchtower" app/src/main/java app/src/test/java app/src/androidTest/java
```

Expected: no production references. Migration tests may contain legacy SQL strings needed to verify deletion.

- [ ] **Step 5: Commit validation fixes**

If files changed:

```bash
git add app/src/main/java app/src/test/java app/src/androidTest/java
git commit -m "test: verify breach protocol epic"
```

If no files changed, do not create an empty commit.

## Plan Self-Review

Spec coverage:

- Watchtower removal: Ticket 1.
- Character-summary/player-stat budget removal: Ticket 1.
- H3 gameplay spatial unit: Ticket 2.
- Lazy deterministic breach generation: Ticket 2.
- Breach persistence: Ticket 2.
- Scan, approach, upload completion, controlled state: Tickets 3 and 4.
- Map overlays: Ticket 5.
- Dynamic markers: Ticket 6.
- Controlled H3 fog reveal: Ticket 7.
- Verification: Ticket 8.

Type consistency:

- `h3CellId` is a `String` in domain, entity, DAO, repository, and use cases.
- `BreachNodeDefinition.controlledH3CellIds` is a `Set<String>`.
- `ControlledBreachRevealSnapshot` carries controlled H3 cell ids and is converted to fog tiles only in the map/fog layer.

Execution rule:

- Do not batch all tickets into one commit.
- Do not proceed past a failed verification command without fixing the failure or recording the blocker.
