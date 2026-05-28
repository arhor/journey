# Breach Search Tactical Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make normal exploration use the light map style and conventional map gestures, while breach search switches into a cyberpunk tactical mode with user-anchored follow and remapped drag controls.

**Architecture:** Keep breach mode as explicit map feature state in `MapViewModel`, expose a focused map interaction configuration through `MapUiState.Content`, and make `MapLibreViewMapScreen` declaratively apply either the normal exploration profile or the breach tactical profile to a single long-lived `MapView`. Reuse the existing breach session flow and `PlayerCenteredCameraGestureTracker` rather than inventing a parallel system.

**Tech Stack:** Kotlin, Jetpack Compose, MapLibre Android SDK, Hilt, kotlinx.coroutines Flow, JUnit, Kotest, MockK, Gradle

---

### Task 1: Add failing state tests for effective map mode and style

**Files:**
- Modify: `app/src/test/java/com/github/arhor/journey/feature/map/MapViewModelTest.kt`
- Test: `app/src/test/java/com/github/arhor/journey/feature/map/MapViewModelTest.kt`

- [ ] **Step 1: Write failing tests for the new map interaction state**

```kotlin
    @Test
    fun `uiState should expose light exploration mode before breach pulse`() = runTest {
        // Given
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = createFixture(
            selectedMapStyle = MapStyle.styleById("urban-noir")!!,
        )

        try {
            // When
            val actual = fixture.viewModel.awaitContent()

            // Then
            actual.mapMode shouldBe MapMode.Exploration(styleUri = "asset://map/styles/light.json")
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `uiState should expose breach tactical mode after pulse`() = runTest {
        // Given
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val actorLocation = GeoPoint(lat = 50.4500, lon = 30.5200)
        val breachRecord = breachRecord(
            id = "breach-node:v1:h3r9:cell-9",
            cellId = "cell-9",
            location = actorLocation,
        )
        val findNearestBreachNode = mockk<FindNearestBreachNodeUseCase>()
        val discoverBreachNode = mockk<DiscoverBreachNodeUseCase>()
        coEvery { findNearestBreachNode.invoke(actorLocation) } returns Output.Success(breachRecord)
        coEvery {
            discoverBreachNode.invoke(
                id = breachRecord.definition.id,
                actorLocation = actorLocation,
            )
        } returns Output.Success(
            BreachNodeState(
                breachNodeId = breachRecord.definition.id,
                h3CellId = breachRecord.definition.h3CellId,
                discoveredAt = FIXED_INSTANT,
                controlledAt = null,
                lockdownUntil = null,
                updatedAt = FIXED_INSTANT,
            ),
        )
        val fixture = createFixture(
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = actorLocation,
            ),
            findNearestBreachNode = findNearestBreachNode,
            discoverBreachNode = discoverBreachNode,
        )

        try {
            fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(MapIntent.PulseClicked)
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent { content ->
                content.mapMode is MapMode.BreachTactical
            }
            actual.mapMode shouldBe MapMode.BreachTactical(
                styleUri = "asset://map/styles/cyberpunk.json",
                isLocationAvailable = true,
            )
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }
```

- [ ] **Step 2: Run the focused tests to verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.MapViewModelTest"`
Expected: FAIL because `MapMode` and `mapMode` do not exist yet.

- [ ] **Step 3: Add minimal UI-facing map mode model and expose it from UI state**

```kotlin
@Immutable
sealed interface MapMode {
    val styleUri: String

    @Immutable
    data class Exploration(
        override val styleUri: String,
    ) : MapMode

    @Immutable
    data class BreachTactical(
        override val styleUri: String,
        val isLocationAvailable: Boolean,
    ) : MapMode
}
```

```kotlin
    data class Content(
        val northResetRequestToken: Int,
        val isExplorationTrackingActive: Boolean,
        val explorationTrackingCadence: ExplorationTrackingCadence,
        val explorationTrackingStatus: ExplorationTrackingStatus,
        val breachProtocol: BreachProtocolUiState,
        val isStartupSplashVisible: Boolean,
        @StringRes val startupSplashMessage: Int,
        val mapMode: MapMode,
        val visibleObjects: List<MapObjectUiModel>,
        val fogOfWar: FogOfWarUiState,
    ) : MapUiState
```

- [ ] **Step 4: Implement minimal `MapViewModel` mapping for exploration vs breach tactical**

```kotlin
private fun State.toMapMode(
    selectedMapStyle: MapStyle,
    trackingSession: ExplorationTrackingSession,
): MapMode {
    val isBreachMode = breachProtocol !is BreachProtocolUiState.Idle
    return if (isBreachMode) {
        MapMode.BreachTactical(
            styleUri = MapStyle.styleById("cyberpunk")!!.value,
            isLocationAvailable = trackingSession.lastKnownLocation != null,
        )
    } else {
        MapMode.Exploration(
            styleUri = MapStyle.styleById("light")!!.value,
        )
    }
}
```

- [ ] **Step 5: Re-run the focused tests to verify GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.MapViewModelTest"`
Expected: PASS for the new mode assertions, with any unrelated failures addressed before moving on.

- [ ] **Step 6: Commit the state-model change**

```bash
git add app/src/test/java/com/github/arhor/journey/feature/map/MapViewModelTest.kt \
  app/src/main/java/com/github/arhor/journey/feature/map/MapUiState.kt \
  app/src/main/java/com/github/arhor/journey/feature/map/MapViewModel.kt \
  app/src/main/java/com/github/arhor/journey/feature/map/MapMode.kt
git commit -m "feat: add tactical map mode state"
```

### Task 2: Add failing tests for restoration and degraded breach behavior

**Files:**
- Modify: `app/src/test/java/com/github/arhor/journey/feature/map/MapViewModelTest.kt`
- Test: `app/src/test/java/com/github/arhor/journey/feature/map/MapViewModelTest.kt`

- [ ] **Step 1: Write failing tests for dismissal restoration and missing-location degradation**

```kotlin
    @Test
    fun `uiState should restore exploration mode when breach panel is dismissed`() = runTest {
        // Given
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val actorLocation = GeoPoint(lat = 50.4500, lon = 30.5200)
        val breachRecord = breachRecord(
            id = "breach-node:v1:h3r9:cell-10",
            cellId = "cell-10",
            location = actorLocation,
        )
        val fixture = createPulseLockedFixture(actorLocation, breachRecord)

        try {
            fixture.viewModel.awaitContent()
            fixture.viewModel.dispatch(MapIntent.PulseClicked)
            advanceUntilIdle()

            // When
            fixture.viewModel.dispatch(MapIntent.DismissBreachPanel)
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent()
            actual.mapMode shouldBe MapMode.Exploration(styleUri = "asset://map/styles/light.json")
            actual.breachProtocol shouldBe BreachProtocolUiState.Idle
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `uiState should keep breach tactical mode and disable upload affordance when location becomes unavailable`() = runTest {
        // Given
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val actorLocation = GeoPoint(lat = 50.4500, lon = 30.5200)
        val breachRecord = breachRecord(
            id = "breach-node:v1:h3r9:cell-11",
            cellId = "cell-11",
            location = actorLocation,
        )
        val trackingSessionFlow = MutableStateFlow(
            Output.Success(
                ExplorationTrackingSession(
                    isActive = true,
                    status = ExplorationTrackingStatus.TRACKING,
                    lastKnownLocation = actorLocation,
                ),
            ),
        )
        val fixture = createPulseLockedFixture(
            actorLocation = actorLocation,
            breachRecord = breachRecord,
            trackingSessionFlow = trackingSessionFlow,
        )

        try {
            fixture.viewModel.awaitContent()
            fixture.viewModel.dispatch(MapIntent.PulseClicked)
            advanceUntilIdle()

            // When
            trackingSessionFlow.value = Output.Success(
                ExplorationTrackingSession(
                    isActive = true,
                    status = ExplorationTrackingStatus.TEMPORARILY_UNAVAILABLE,
                    lastKnownLocation = null,
                ),
            )
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent { content ->
                content.mapMode is MapMode.BreachTactical &&
                    (content.breachProtocol as? BreachProtocolUiState.SignalLocked)?.canStartUpload == false
            }
            actual.mapMode shouldBe MapMode.BreachTactical(
                styleUri = "asset://map/styles/cyberpunk.json",
                isLocationAvailable = false,
            )
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }
```

- [ ] **Step 2: Run the focused tests to verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.MapViewModelTest"`
Expected: FAIL because breach UI state is not recomputed from tracking-session loss and restoration behavior is incomplete.

- [ ] **Step 3: Implement minimal breach-state recomputation from tracking session changes**

```kotlin
private fun State.resolveBreachUiState(
    trackingSession: ExplorationTrackingSession,
): BreachProtocolUiState {
    val lockedBreach = lockedBreach ?: return breachProtocol
    if (breachPhase != BreachSessionPhase.SIGNAL_LOCKED) {
        return breachProtocol
    }

    val actorLocation = trackingSession.lastKnownLocation
    val distanceMeters = actorLocation?.distanceTo(lockedBreach.definition.location)
    val canStartUpload = actorLocation != null &&
        distanceMeters != null &&
        distanceMeters <= lockedBreach.definition.interactionRadiusMeters

    return lockedBreach.copy(
        distanceMeters = distanceMeters,
        canDiscover = canStartUpload,
        canStartUpload = canStartUpload,
    ).toSignalLockedUiState(
        missingLocationMessage = if (actorLocation == null) "Location required to continue breach scan." else null,
    )
}
```

- [ ] **Step 4: Re-run the focused tests to verify GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.MapViewModelTest"`
Expected: PASS for restoration and degraded tactical mode behavior.

- [ ] **Step 5: Commit the restoration/degradation logic**

```bash
git add app/src/test/java/com/github/arhor/journey/feature/map/MapViewModelTest.kt \
  app/src/main/java/com/github/arhor/journey/feature/map/BreachProtocolUiState.kt \
  app/src/main/java/com/github/arhor/journey/feature/map/MapViewModel.kt
git commit -m "feat: degrade breach tactical mode without location"
```

### Task 3: Add failing tests for default style behavior

**Files:**
- Modify: `app/src/test/java/com/github/arhor/journey/domain/model/MapStyleTest.kt`
- Modify: `app/src/test/java/com/github/arhor/journey/domain/usecase/MapStyleSettingsUseCaseTest.kt`
- Test: `app/src/test/java/com/github/arhor/journey/domain/model/MapStyleTest.kt`

- [ ] **Step 1: Write failing tests for the light default style**

```kotlin
    @Test
    fun `defaultStyle should resolve to light style`() {
        // Given
        val expected = MapStyle.bundle(
            id = "light",
            name = "Light",
            value = "asset://map/styles/light.json",
        )

        // When
        val actual = MapStyle.defaultStyle

        // Then
        actual shouldBe expected
    }
```

- [ ] **Step 2: Run the focused tests to verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.domain.model.MapStyleTest" --tests "com.github.arhor.journey.domain.usecase.MapStyleSettingsUseCaseTest"`
Expected: FAIL because `MapStyle.defaultStyle` is still cyberpunk.

- [ ] **Step 3: Change the default style and keep cyberpunk available for tactical mode**

```kotlin
        val light = bundle(
            id = "light",
            name = "Light",
            value = "asset://map/styles/light.json",
        )

        val cyberpunk = bundle(
            id = "cyberpunk",
            name = "Cyberpunk",
            value = "asset://map/styles/cyberpunk.json",
        )

        val defaultStyle = light

        val availableStyles = listOf(
            light,
            cyberpunk,
            bundle(
                id = "urban-noir",
                name = "Urban Noir",
                value = "asset://map/styles/urban-noir.json",
            ),
        )
```

- [ ] **Step 4: Re-run the focused tests to verify GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.domain.model.MapStyleTest" --tests "com.github.arhor.journey.domain.usecase.MapStyleSettingsUseCaseTest"`
Expected: PASS.

- [ ] **Step 5: Commit the style default change**

```bash
git add app/src/test/java/com/github/arhor/journey/domain/model/MapStyleTest.kt \
  app/src/test/java/com/github/arhor/journey/domain/usecase/MapStyleSettingsUseCaseTest.kt \
  app/src/main/java/com/github/arhor/journey/domain/model/MapStyle.kt
git commit -m "feat: make light the default map style"
```

### Task 4: Add failing interop tests for mode-driven gesture policy

**Files:**
- Create: `app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/MapInteractionModeControllerTest.kt`
- Create: `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/MapInteractionModeController.kt`
- Test: `app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/MapInteractionModeControllerTest.kt`

- [ ] **Step 1: Write failing tests for exploration and breach tactical application**

```kotlin
class MapInteractionModeControllerTest {

    @Test
    fun `apply should enable standard gestures for exploration mode`() {
        // Given
        val uiSettings = FakeUiSettings()
        val controller = MapInteractionModeController()

        // When
        controller.apply(
            mode = MapMode.Exploration(styleUri = "asset://map/styles/light.json"),
            target = FakeMapInteractionTarget(uiSettings = uiSettings),
        )

        // Then
        uiSettings.isScrollGesturesEnabled shouldBe true
        uiSettings.isRotateGesturesEnabled shouldBe true
        uiSettings.isTiltGesturesEnabled shouldBe true
        uiSettings.isZoomGesturesEnabled shouldBe true
    }

    @Test
    fun `apply should disable pan and require follow capable tactical controls for breach mode`() {
        // Given
        val uiSettings = FakeUiSettings()
        val controller = MapInteractionModeController()

        // When
        controller.apply(
            mode = MapMode.BreachTactical(
                styleUri = "asset://map/styles/cyberpunk.json",
                isLocationAvailable = true,
            ),
            target = FakeMapInteractionTarget(uiSettings = uiSettings),
        )

        // Then
        uiSettings.isScrollGesturesEnabled shouldBe false
        uiSettings.isHorizontalScrollGesturesEnabled shouldBe false
        uiSettings.isRotateGesturesEnabled shouldBe false
        uiSettings.isTiltGesturesEnabled shouldBe false
        uiSettings.isZoomGesturesEnabled shouldBe true
    }
}
```

- [ ] **Step 2: Run the focused tests to verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.MapInteractionModeControllerTest"`
Expected: FAIL because the controller does not exist yet.

- [ ] **Step 3: Implement the minimal extracted controller**

```kotlin
internal class MapInteractionModeController {

    fun apply(
        mode: MapMode,
        target: MapInteractionTarget,
    ) {
        when (mode) {
            is MapMode.Exploration -> target.applyExplorationGestures()
            is MapMode.BreachTactical -> target.applyBreachTacticalGestures()
        }
    }
}

internal interface MapInteractionTarget {
    fun applyExplorationGestures()
    fun applyBreachTacticalGestures()
}
```

- [ ] **Step 4: Re-run the focused tests to verify GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.MapInteractionModeControllerTest"`
Expected: PASS.

- [ ] **Step 5: Commit the extracted interaction controller**

```bash
git add app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/MapInteractionModeControllerTest.kt \
  app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/MapInteractionModeController.kt
git commit -m "refactor: extract map interaction mode controller"
```

### Task 5: Apply the interaction profiles in MapLibre without recreating the map view

**Files:**
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapScreen.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/MapLibreViewMapScreen.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/MapInteractionModeController.kt`
- Test: `app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/MapInteractionModeControllerTest.kt`

- [ ] **Step 1: Add failing tests or expectations for idempotent style/mode application**

```kotlin
    @Test
    fun `apply should avoid reapplying the same mode twice`() {
        // Given
        val target = RecordingMapInteractionTarget()
        val controller = MapInteractionModeController()
        val mode = MapMode.Exploration(styleUri = "asset://map/styles/light.json")

        // When
        controller.apply(mode = mode, target = target)
        controller.apply(mode = mode, target = target)

        // Then
        target.explorationApplyCount shouldBe 1
    }
```

- [ ] **Step 2: Run the focused tests to verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.MapInteractionModeControllerTest"`
Expected: FAIL because duplicate application is not tracked yet.

- [ ] **Step 3: Update `MapLibreViewMapScreen` to take `mapMode` and keep one `MapView` alive**

```kotlin
fun MapLibreViewMapScreen(
    modifier: Modifier = Modifier,
    mapMode: MapMode = MapMode.Exploration(styleUri = "asset://map/styles/light.json"),
    visibleObjects: List<MapObjectUiModel> = emptyList(),
    fogOfWar: FogOfWarRenderState = FogOfWarRenderState(),
    ...
)
```

```kotlin
AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { context -> createConfiguredMapView(context, ...) },
    update = { mapView ->
        mapViewHandles[mapView]?.let { handle ->
            handle.applyStyleIfNeeded(mapMode.styleUri)
            handle.applyInteractionMode(mapMode)
            updateMapLayerControllers(
                fogLayerController = handle.fogLayerController,
                fogOfWar = fogOfWar,
                objectLayerController = handle.objectLayerController,
                visibleObjects = visibleObjects,
            )
        }
    },
    onRelease = { mapView -> ... },
)
```

- [ ] **Step 4: Update `NativeCameraGestureController` to support both exploration and breach tactical profiles**

```kotlin
    fun setMode(mode: MapMode) {
        currentMode = mode
        val map = map ?: return
        when (mode) {
            is MapMode.Exploration -> {
                isCustomCameraGestureActive = false
                restoreFreeCameraMode(map)
            }
            is MapMode.BreachTactical -> {
                if (mode.isLocationAvailable) {
                    restoreTrackingCameraMode(map)
                } else {
                    map.locationComponent.setCameraMode(CameraMode.NONE)
                }
            }
        }
    }
```

- [ ] **Step 5: Re-run the focused tests to verify GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.MapInteractionModeControllerTest"`
Expected: PASS.

- [ ] **Step 6: Compile Kotlin to verify the interop wiring**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit the interop rework**

```bash
git add app/src/main/java/com/github/arhor/journey/feature/map/MapScreen.kt \
  app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/MapLibreViewMapScreen.kt \
  app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/MapInteractionModeController.kt \
  app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/MapInteractionModeControllerTest.kt
git commit -m "feat: apply breach tactical map interaction mode"
```

### Task 6: Run the full relevant verification suite and inspect architecture drift

**Files:**
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapViewModel.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/MapLibreViewMapScreen.kt`
- Modify: `app/src/test/java/com/github/arhor/journey/feature/map/MapViewModelTest.kt`

- [ ] **Step 1: Run all relevant JVM tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 2: Re-run Kotlin compilation after any final fixups**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Inspect the final diff for architecture drift**

Run: `git diff --stat && git diff -- app/src/main/java/com/github/arhor/journey/feature/map/MapViewModel.kt app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/MapLibreViewMapScreen.kt`
Expected: map mode stays explicit, no hardcoded deep UI style logic, no duplicated gesture-state system.

- [ ] **Step 4: Commit the final implementation polish if needed**

```bash
git add app/src/main/java/com/github/arhor/journey/feature/map/MapViewModel.kt \
  app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/MapLibreViewMapScreen.kt \
  app/src/test/java/com/github/arhor/journey/feature/map/MapViewModelTest.kt
git commit -m "test: verify breach tactical mode integration"
```
