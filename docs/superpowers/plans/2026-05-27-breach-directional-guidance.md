# Breach Directional Guidance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace breach lookup's signal-strength-first interaction with an exact-bearing directional HUD: a floating arrow during tracking that collapses into an on-target marker when the player reaches upload range.

**Architecture:** Keep the breach session lifecycle in `MapViewModel`, add a focused `BreachDirectionalGuidanceUiState` plus presenter for bearing/range-derived HUD state, and render that state as a Compose overlay above the existing map surface. Reuse the current tactical breach mode and upload flow; do not introduce route rendering, map annotations, or a parallel navigation subsystem.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Hilt, MapLibre Android SDK, kotlinx.coroutines Flow, JUnit, Kotest, MockK, Gradle

---

## Planned File Map

- Create: `app/src/main/java/com/github/arhor/journey/feature/map/BreachDirectionalGuidanceUiState.kt`
  Responsibility: UI-facing sealed state for `Hidden`, `Unavailable`, `FloatingArrow`, and `OnTarget`.
- Create: `app/src/main/java/com/github/arhor/journey/feature/map/presentation/BreachDirectionalGuidancePresenter.kt`
  Responsibility: map `BreachNode` plus current actor location into directional HUD state.
- Create: `app/src/test/java/com/github/arhor/journey/feature/map/presentation/BreachDirectionalGuidancePresenterTest.kt`
  Responsibility: cover floating-arrow, on-target, and unavailable transitions.
- Modify: `app/src/main/java/com/github/arhor/journey/domain/model/GeoPoint.kt`
  Responsibility: add exact `bearingTo` helper alongside the existing `distanceTo`.
- Create: `app/src/test/java/com/github/arhor/journey/domain/model/GeoPointTest.kt`
  Responsibility: verify stable bearing calculations for representative directions.
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/BreachProtocolUiState.kt`
  Responsibility: slim `SignalLocked` to supporting panel data by removing `signalStrengthPercent`.
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapUiState.kt`
  Responsibility: expose `breachGuidance` in `MapUiState.Content`.
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapViewModel.kt`
  Responsibility: derive `breachGuidance`, keep tactical mode behavior, and update locked-breach panel state.
- Modify: `app/src/test/java/com/github/arhor/journey/feature/map/MapViewModelTest.kt`
  Responsibility: verify guidance state emission, upload-range transition, location-loss fallback, and dismissal cleanup.
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/BreachProtocolOverlay.kt`
  Responsibility: render the directional HUD and compact supporting control panel.
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapScreen.kt`
  Responsibility: pass `breachGuidance` into the overlay stack and expose stable test tags.
- Modify: `app/src/androidTest/java/com/github/arhor/journey/feature/map/MapScreenTest.kt`
  Responsibility: verify floating arrow, on-target marker, and unavailable messaging in Compose UI.
- Modify: `app/src/main/res/values/strings.xml`
  Responsibility: remove signal-strength-first copy from the lookup experience and add compact tracking copy only if needed.

### Task 1: Add exact bearing support in `GeoPoint`

**Files:**
- Modify: `app/src/main/java/com/github/arhor/journey/domain/model/GeoPoint.kt`
- Create: `app/src/test/java/com/github/arhor/journey/domain/model/GeoPointTest.kt`
- Test: `app/src/test/java/com/github/arhor/journey/domain/model/GeoPointTest.kt`

- [ ] **Step 1: Write the failing bearing tests**

```kotlin
package com.github.arhor.journey.domain.model

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.Test

class GeoPointTest {

    @Test
    fun `bearingTo should return north bearing when target is due north`() {
        // Given
        val origin = GeoPoint(lat = 0.0, lon = 0.0)
        val target = GeoPoint(lat = 1.0, lon = 0.0)

        // When
        val actual = origin.bearingTo(target)

        // Then
        actual shouldBe (0.0 plusOrMinus 0.001)
    }

    @Test
    fun `bearingTo should return east bearing when target is due east`() {
        // Given
        val origin = GeoPoint(lat = 0.0, lon = 0.0)
        val target = GeoPoint(lat = 0.0, lon = 1.0)

        // When
        val actual = origin.bearingTo(target)

        // Then
        actual shouldBe (90.0 plusOrMinus 0.001)
    }
}
```

- [ ] **Step 2: Run the new test to verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.domain.model.GeoPointTest"`
Expected: FAIL because `GeoPoint.bearingTo` does not exist yet.

- [ ] **Step 3: Implement the minimal bearing helper**

```kotlin
package com.github.arhor.journey.domain.model

import com.github.arhor.journey.domain.internal.distanceMeters
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class GeoPoint(
    val lat: Double,
    val lon: Double,
) {
    fun distanceTo(that: GeoPoint): Double = distanceMeters(
        lat1 = lat,
        lon1 = lon,
        lat2 = that.lat,
        lon2 = that.lon
    )

    fun bearingTo(that: GeoPoint): Double {
        val lat1 = Math.toRadians(lat)
        val lat2 = Math.toRadians(that.lat)
        val deltaLon = Math.toRadians(that.lon - lon)
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        val bearing = Math.toDegrees(atan2(y, x))

        return (bearing + 360.0) % 360.0
    }
}
```

- [ ] **Step 4: Re-run the bearing test to verify GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.domain.model.GeoPointTest"`
Expected: PASS with both `bearingTo` assertions green.

- [ ] **Step 5: Commit the bearing helper**

```bash
git add app/src/main/java/com/github/arhor/journey/domain/model/GeoPoint.kt \
  app/src/test/java/com/github/arhor/journey/domain/model/GeoPointTest.kt
git commit -m "feat: add geo bearing helper"
```

### Task 2: Add breach directional guidance state and presenter

**Files:**
- Create: `app/src/main/java/com/github/arhor/journey/feature/map/BreachDirectionalGuidanceUiState.kt`
- Create: `app/src/main/java/com/github/arhor/journey/feature/map/presentation/BreachDirectionalGuidancePresenter.kt`
- Create: `app/src/test/java/com/github/arhor/journey/feature/map/presentation/BreachDirectionalGuidancePresenterTest.kt`
- Test: `app/src/test/java/com/github/arhor/journey/feature/map/presentation/BreachDirectionalGuidancePresenterTest.kt`

- [ ] **Step 1: Write the failing presenter tests**

```kotlin
package com.github.arhor.journey.feature.map.presentation

import com.github.arhor.journey.domain.model.BreachNode
import com.github.arhor.journey.domain.model.BreachNodeDefinition
import com.github.arhor.journey.domain.model.BreachNodePhase
import com.github.arhor.journey.domain.model.BreachNodeState
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.feature.map.BreachDirectionalGuidanceUiState
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant

class BreachDirectionalGuidancePresenterTest {

    private val subject = BreachDirectionalGuidancePresenter()

    @Test
    fun `present should return floating arrow when actor is outside upload radius`() {
        // Given
        val actorLocation = GeoPoint(lat = 0.0, lon = 0.0)
        val breach = breachNode(
            location = GeoPoint(lat = 0.0, lon = 1.0),
            interactionRadiusMeters = 30.0,
            canStartUpload = false,
        )

        // When
        val actual = subject.present(
            breach = breach,
            actorLocation = actorLocation,
        )

        // Then
        actual shouldBe BreachDirectionalGuidanceUiState.FloatingArrow(
            breachNodeId = breach.definition.id,
            districtName = breach.definition.districtName,
            bearingDegrees = 90.0,
            distanceMeters = actorLocation.distanceTo(breach.definition.location).toInt(),
            canStartUpload = false,
        )
    }

    @Test
    fun `present should return on target when actor can start upload`() {
        // Given
        val actorLocation = GeoPoint(lat = 50.45, lon = 30.52)
        val breach = breachNode(
            location = actorLocation,
            interactionRadiusMeters = 30.0,
            canStartUpload = true,
        )

        // When
        val actual = subject.present(
            breach = breach,
            actorLocation = actorLocation,
        )

        // Then
        actual shouldBe BreachDirectionalGuidanceUiState.OnTarget(
            breachNodeId = breach.definition.id,
            districtName = breach.definition.districtName,
            distanceMeters = 0,
            canStartUpload = true,
        )
    }

    @Test
    fun `present should return unavailable when actor location is missing`() {
        // Given
        val breach = breachNode(
            location = GeoPoint(lat = 50.45, lon = 30.52),
            interactionRadiusMeters = 30.0,
            canStartUpload = false,
        )

        // When
        val actual = subject.present(
            breach = breach,
            actorLocation = null,
        )

        // Then
        actual shouldBe BreachDirectionalGuidanceUiState.Unavailable(
            breachNodeId = breach.definition.id,
            districtName = breach.definition.districtName,
            message = "Location required to continue breach scan.",
        )
    }

    private fun breachNode(
        location: GeoPoint,
        interactionRadiusMeters: Double,
        canStartUpload: Boolean,
    ): BreachNode =
        BreachNode(
            definition = BreachNodeDefinition(
                id = "breach-node:v1:h3r9:test-cell",
                h3CellId = "test-cell",
                districtName = "Downtown",
                description = "Signal source",
                location = location,
                interactionRadiusMeters = interactionRadiusMeters,
                controlledH3CellIds = setOf("test-cell"),
            ),
            state = BreachNodeState(
                breachNodeId = "breach-node:v1:h3r9:test-cell",
                h3CellId = "test-cell",
                discoveredAt = Instant.parse("2026-05-01T10:15:30Z"),
                controlledAt = null,
                lockdownUntil = null,
                updatedAt = Instant.parse("2026-05-01T10:15:30Z"),
            ),
            phase = BreachNodePhase.SIGNAL_LOCKED,
            distanceMeters = if (canStartUpload) 0.0 else 111_194.9,
            canDiscover = canStartUpload,
            canStartUpload = canStartUpload,
        )
}
```

- [ ] **Step 2: Run the presenter test to verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.presentation.BreachDirectionalGuidancePresenterTest"`
Expected: FAIL because the guidance state and presenter do not exist yet.

- [ ] **Step 3: Implement the guidance model and presenter**

```kotlin
package com.github.arhor.journey.feature.map

import androidx.compose.runtime.Immutable

sealed interface BreachDirectionalGuidanceUiState {

    @Immutable
    data object Hidden : BreachDirectionalGuidanceUiState

    @Immutable
    data class Unavailable(
        val breachNodeId: String,
        val districtName: String,
        val message: String,
    ) : BreachDirectionalGuidanceUiState

    @Immutable
    data class FloatingArrow(
        val breachNodeId: String,
        val districtName: String,
        val bearingDegrees: Double,
        val distanceMeters: Int,
        val canStartUpload: Boolean,
    ) : BreachDirectionalGuidanceUiState

    @Immutable
    data class OnTarget(
        val breachNodeId: String,
        val districtName: String,
        val distanceMeters: Int,
        val canStartUpload: Boolean,
    ) : BreachDirectionalGuidanceUiState
}
```

```kotlin
package com.github.arhor.journey.feature.map.presentation

import com.github.arhor.journey.domain.model.BreachNode
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.feature.map.BreachDirectionalGuidanceUiState
import javax.inject.Inject

class BreachDirectionalGuidancePresenter @Inject constructor() {

    fun present(
        breach: BreachNode,
        actorLocation: GeoPoint?,
    ): BreachDirectionalGuidanceUiState {
        if (actorLocation == null) {
            return BreachDirectionalGuidanceUiState.Unavailable(
                breachNodeId = breach.definition.id,
                districtName = breach.definition.districtName,
                message = "Location required to continue breach scan.",
            )
        }

        val distanceMeters = actorLocation.distanceTo(breach.definition.location).toInt()

        return if (breach.canStartUpload) {
            BreachDirectionalGuidanceUiState.OnTarget(
                breachNodeId = breach.definition.id,
                districtName = breach.definition.districtName,
                distanceMeters = distanceMeters,
                canStartUpload = true,
            )
        } else {
            BreachDirectionalGuidanceUiState.FloatingArrow(
                breachNodeId = breach.definition.id,
                districtName = breach.definition.districtName,
                bearingDegrees = actorLocation.bearingTo(breach.definition.location),
                distanceMeters = distanceMeters,
                canStartUpload = false,
            )
        }
    }
}
```

- [ ] **Step 4: Re-run the presenter test to verify GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.presentation.BreachDirectionalGuidancePresenterTest"`
Expected: PASS with floating-arrow, on-target, and unavailable assertions green.

- [ ] **Step 5: Commit the guidance model and presenter**

```bash
git add app/src/main/java/com/github/arhor/journey/feature/map/BreachDirectionalGuidanceUiState.kt \
  app/src/main/java/com/github/arhor/journey/feature/map/presentation/BreachDirectionalGuidancePresenter.kt \
  app/src/test/java/com/github/arhor/journey/feature/map/presentation/BreachDirectionalGuidancePresenterTest.kt
git commit -m "feat: add breach directional guidance state"
```

### Task 3: Wire directional guidance into `MapViewModel`

**Files:**
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/BreachProtocolUiState.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapUiState.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapViewModel.kt`
- Modify: `app/src/test/java/com/github/arhor/journey/feature/map/MapViewModelTest.kt`
- Test: `app/src/test/java/com/github/arhor/journey/feature/map/MapViewModelTest.kt`

- [ ] **Step 1: Write the failing view-model tests for guidance and cleanup**

```kotlin
    @Test
    fun `uiState should expose floating breach guidance when locked breach is outside upload radius`() = runTest {
        // Given
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val actorLocation = GeoPoint(lat = 0.0, lon = 0.0)
        val breachLocation = GeoPoint(lat = 0.0, lon = 0.01)
        val breachRecord = breachRecord(
            id = "breach-node:v1:h3r9:cell-guidance",
            cellId = "cell-guidance",
            location = breachLocation,
        )
        val findNearestBreachNode = mockk<FindNearestBreachNodeUseCase>()
        coEvery { findNearestBreachNode.invoke(actorLocation) } returns Output.Success(breachRecord)
        val fixture = createFixture(
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = actorLocation,
            ),
            findNearestBreachNode = findNearestBreachNode,
        )

        try {
            fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(MapIntent.PulseClicked)
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent { content ->
                content.breachGuidance is BreachDirectionalGuidanceUiState.FloatingArrow
            }
            actual.breachGuidance shouldBe BreachDirectionalGuidanceUiState.FloatingArrow(
                breachNodeId = breachRecord.definition.id,
                districtName = breachRecord.definition.districtName,
                bearingDegrees = actorLocation.bearingTo(breachLocation),
                distanceMeters = actorLocation.distanceTo(breachLocation).toInt(),
                canStartUpload = false,
            )
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `uiState should expose on target breach guidance when actor reaches interaction radius`() = runTest {
        // Given
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val actorLocation = GeoPoint(lat = 50.45, lon = 30.52)
        val breachRecord = breachRecord(
            id = "breach-node:v1:h3r9:cell-on-target",
            cellId = "cell-on-target",
            location = actorLocation,
        )
        val findNearestBreachNode = mockk<FindNearestBreachNodeUseCase>()
        coEvery { findNearestBreachNode.invoke(actorLocation) } returns Output.Success(breachRecord)
        val fixture = createFixture(
            trackingSession = ExplorationTrackingSession(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = actorLocation,
            ),
            findNearestBreachNode = findNearestBreachNode,
        )

        try {
            fixture.viewModel.awaitContent()

            // When
            fixture.viewModel.dispatch(MapIntent.PulseClicked)
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent { content ->
                content.breachGuidance is BreachDirectionalGuidanceUiState.OnTarget
            }
            actual.breachGuidance shouldBe BreachDirectionalGuidanceUiState.OnTarget(
                breachNodeId = breachRecord.definition.id,
                districtName = breachRecord.definition.districtName,
                distanceMeters = 0,
                canStartUpload = true,
            )
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }

    @Test
    fun `uiState should hide breach guidance when breach panel is dismissed`() = runTest {
        // Given
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val actorLocation = GeoPoint(lat = 50.45, lon = 30.52)
        val breachRecord = breachRecord(
            id = "breach-node:v1:h3r9:cell-dismiss",
            cellId = "cell-dismiss",
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
        } returns Output.Success(breachRecord.state!!)
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
            fixture.viewModel.dispatch(MapIntent.PulseClicked)
            advanceUntilIdle()

            // When
            fixture.viewModel.dispatch(MapIntent.DismissBreachPanel)
            advanceUntilIdle()

            // Then
            val actual = fixture.viewModel.awaitContent()
            actual.breachGuidance shouldBe BreachDirectionalGuidanceUiState.Hidden
        } finally {
            tearDownMainDispatcher(fixture.viewModel)
        }
    }
```

- [ ] **Step 2: Run the focused view-model test class to verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.MapViewModelTest"`
Expected: FAIL because `MapUiState.Content` does not expose `breachGuidance`, `SignalLocked` still expects `signalStrengthPercent`, and the view model does not derive directional state yet.

- [ ] **Step 3: Implement minimal state-model and view-model wiring**

```kotlin
sealed interface BreachProtocolUiState {

    @Immutable
    data object Idle : BreachProtocolUiState

    @Immutable
    data object Scanning : BreachProtocolUiState

    @Immutable
    data class SignalLocked(
        val breachNodeId: String,
        val districtName: String,
        val distanceMeters: Int?,
        val canStartUpload: Boolean,
        val disabledReason: String?,
    ) : BreachProtocolUiState

    // Uploading and Completed stay unchanged.
}
```

```kotlin
data class Content(
    val northResetRequestToken: Int,
    val isExplorationTrackingActive: Boolean,
    val explorationTrackingCadence: ExplorationTrackingCadence,
    val explorationTrackingStatus: ExplorationTrackingStatus,
    val breachProtocol: BreachProtocolUiState,
    val breachGuidance: BreachDirectionalGuidanceUiState,
    val isStartupSplashVisible: Boolean,
    @StringRes val startupSplashMessage: Int,
    val mapMode: MapMode,
    val mapStyleUri: String,
    val visibleObjects: List<MapObjectUiModel>,
    val fogOfWar: FogOfWarUiState,
) : MapUiState
```

```kotlin
class MapViewModel @Inject constructor(
    private val observeSelectedMapStyle: ObserveSelectedMapStyleUseCase,
    private val fogOfWarControllerFactory: FogOfWarController.Factory,
    private val observeExplorationTrackingSession: ObserveExplorationTrackingSessionUseCase,
    private val startExplorationTrackingSession: StartExplorationTrackingSessionUseCase,
    private val findNearestBreachNode: FindNearestBreachNodeUseCase,
    private val discoverBreachNode: DiscoverBreachNodeUseCase,
    private val completeBreach: CompleteBreachUseCase,
    private val observeVisibleBreachNodes: ObserveVisibleBreachNodesUseCase,
    private val observeControlledBreachRevealCells: ObserveControlledBreachRevealCellsUseCase,
    private val breachNodePresenter: BreachNodePresenter,
    private val breachDirectionalGuidancePresenter: BreachDirectionalGuidancePresenter,
) : MviViewModel<MapUiState, MapEffect, MapIntent>(initialState = MapUiState.Loading) {
```

```kotlin
private fun State.resolveBreachGuidanceUiState(
    trackingSession: ExplorationTrackingSession,
): BreachDirectionalGuidanceUiState {
    if (breachPhase != BreachSessionPhase.SIGNAL_LOCKED) {
        return BreachDirectionalGuidanceUiState.Hidden
    }

    val breach = lockedBreach?.resolveSignalLockedBreach(trackingSession.lastKnownLocation)
        ?: return BreachDirectionalGuidanceUiState.Hidden

    return breachDirectionalGuidancePresenter.present(
        breach = breach,
        actorLocation = trackingSession.lastKnownLocation,
    )
}
```

```kotlin
MapBaseUiState.Content(
    cameraPosition = state.cameraPosition,
    cameraUpdateOrigin = state.cameraUpdateOrigin,
    isUserInteractingCamera = state.isUserInteractingCamera,
    northResetRequestToken = state.northResetRequestToken,
    isExplorationTrackingActive = trackingSessionValue.isActive,
    explorationTrackingCadence = trackingSessionValue.cadence,
    explorationTrackingStatus = trackingSessionValue.status,
    breachProtocol = state.resolveBreachProtocolUiState(trackingSessionValue),
    breachGuidance = state.resolveBreachGuidanceUiState(trackingSessionValue),
    isStartupSplashVisible = state.startupGate.isSplashVisible,
    startupSplashMessage = R.string.map_view_startup_loading_message,
    mapMode = mapMode,
    mapStyleUri = mapMode.styleUri,
    visibleObjects = when (visibleBreachObjectsOutput) {
        is Output.Success -> visibleBreachObjectsOutput.value
        is Output.Failure -> emptyList()
    },
    fogOfWar = fogOfWar,
)
```

```kotlin
private fun BreachNode.toSignalLockedUiState(actorLocation: GeoPoint?): BreachProtocolUiState.SignalLocked {
    val resolvedBreach = resolveSignalLockedBreach(actorLocation)

    return BreachProtocolUiState.SignalLocked(
        breachNodeId = definition.id,
        districtName = definition.districtName,
        distanceMeters = resolvedBreach.distanceMeters?.toInt(),
        canStartUpload = resolvedBreach.canStartUpload,
        disabledReason = when {
            actorLocation == null -> "Location required to continue breach scan."
            resolvedBreach.canStartUpload -> null
            else -> "Move closer to start upload."
        },
    )
}
```

- [ ] **Step 4: Re-run the focused view-model test class to verify GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.MapViewModelTest"`
Expected: PASS with directional guidance, tactical mode, dismissal cleanup, and upload-state assertions green.

- [ ] **Step 5: Commit the view-model wiring**

```bash
git add app/src/main/java/com/github/arhor/journey/feature/map/BreachProtocolUiState.kt \
  app/src/main/java/com/github/arhor/journey/feature/map/MapUiState.kt \
  app/src/main/java/com/github/arhor/journey/feature/map/MapViewModel.kt \
  app/src/test/java/com/github/arhor/journey/feature/map/MapViewModelTest.kt
git commit -m "feat: expose breach directional guidance state"
```

### Task 4: Render the directional HUD and compact control panel

**Files:**
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/BreachProtocolOverlay.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/androidTest/java/com/github/arhor/journey/feature/map/MapScreenTest.kt`
- Test: `app/src/androidTest/java/com/github/arhor/journey/feature/map/MapScreenTest.kt`

- [ ] **Step 1: Write the failing Compose UI tests for the new HUD**

```kotlin
    @Test
    fun `MapContent should show floating guidance arrow when breach guidance is tracking`() {
        // Given
        composeRule.setContent {
            MaterialTheme {
                MapContent(
                    state = contentState(
                        isStartupSplashVisible = false,
                        breachProtocol = BreachProtocolUiState.SignalLocked(
                            breachNodeId = "breach-node:v1:h3r9:test",
                            districtName = "Downtown",
                            distanceMeters = 120,
                            canStartUpload = false,
                            disabledReason = null,
                        ),
                        breachGuidance = BreachDirectionalGuidanceUiState.FloatingArrow(
                            breachNodeId = "breach-node:v1:h3r9:test",
                            districtName = "Downtown",
                            bearingDegrees = 90.0,
                            distanceMeters = 120,
                            canStartUpload = false,
                        ),
                    ),
                    dispatch = {},
                    mapContent = { modifier, _ -> Box(modifier = modifier) },
                )
            }
        }

        // Then
        composeRule.onNodeWithTag(BREACH_GUIDANCE_FLOATING_ARROW_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `MapContent should show on target marker when breach guidance is ready for upload`() {
        // Given
        composeRule.setContent {
            MaterialTheme {
                MapContent(
                    state = contentState(
                        isStartupSplashVisible = false,
                        breachProtocol = BreachProtocolUiState.SignalLocked(
                            breachNodeId = "breach-node:v1:h3r9:test",
                            districtName = "Downtown",
                            distanceMeters = 0,
                            canStartUpload = true,
                            disabledReason = null,
                        ),
                        breachGuidance = BreachDirectionalGuidanceUiState.OnTarget(
                            breachNodeId = "breach-node:v1:h3r9:test",
                            districtName = "Downtown",
                            distanceMeters = 0,
                            canStartUpload = true,
                        ),
                    ),
                    dispatch = {},
                    mapContent = { modifier, _ -> Box(modifier = modifier) },
                )
            }
        }

        // Then
        composeRule.onNodeWithTag(BREACH_GUIDANCE_ON_TARGET_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `MapContent should show unavailable guidance message when location is missing`() {
        // Given
        composeRule.setContent {
            MaterialTheme {
                MapContent(
                    state = contentState(
                        isStartupSplashVisible = false,
                        breachProtocol = BreachProtocolUiState.SignalLocked(
                            breachNodeId = "breach-node:v1:h3r9:test",
                            districtName = "Downtown",
                            distanceMeters = null,
                            canStartUpload = false,
                            disabledReason = "Location required to continue breach scan.",
                        ),
                        breachGuidance = BreachDirectionalGuidanceUiState.Unavailable(
                            breachNodeId = "breach-node:v1:h3r9:test",
                            districtName = "Downtown",
                            message = "Location required to continue breach scan.",
                        ),
                    ),
                    dispatch = {},
                    mapContent = { modifier, _ -> Box(modifier = modifier) },
                )
            }
        }

        // Then
        composeRule.onNodeWithTag(BREACH_GUIDANCE_UNAVAILABLE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Location required to continue breach scan.").assertIsDisplayed()
    }
```

- [ ] **Step 2: Run Android-test assembly to verify RED**

Run: `./gradlew :app:assembleDebugAndroidTest`
Expected: FAIL because `MapContent` does not accept `breachGuidance`, the HUD test tags do not exist, and the overlay still renders signal-strength-first content.

- [ ] **Step 3: Implement the directional HUD and compact panel**

```kotlin
internal const val BREACH_GUIDANCE_FLOATING_ARROW_TEST_TAG = "breach_guidance_floating_arrow"
internal const val BREACH_GUIDANCE_ON_TARGET_TEST_TAG = "breach_guidance_on_target"
internal const val BREACH_GUIDANCE_UNAVAILABLE_TEST_TAG = "breach_guidance_unavailable"

@Composable
internal fun BreachProtocolOverlay(
    state: BreachProtocolUiState,
    guidance: BreachDirectionalGuidanceUiState,
    dispatch: (MapIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        DirectionalGuidanceHud(
            state = guidance,
            modifier = Modifier.fillMaxSize(),
        )

        when (state) {
            BreachProtocolUiState.Idle -> {
                Button(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                    onClick = { dispatch(MapIntent.PulseClicked) },
                ) {
                    Text(text = stringResource(R.string.breach_pulse_button))
                }
            }

            BreachProtocolUiState.Scanning -> {
                TextPanel(
                    text = stringResource(R.string.breach_scanning),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                )
            }

            is BreachProtocolUiState.SignalLocked -> {
                SignalPanel(
                    state = state,
                    dispatch = dispatch,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                )
            }

            is BreachProtocolUiState.Uploading -> { /* existing panel */ }
            is BreachProtocolUiState.Completed -> { /* existing panel */ }
        }
    }
}
```

```kotlin
@Composable
private fun DirectionalGuidanceHud(
    state: BreachDirectionalGuidanceUiState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when (state) {
            BreachDirectionalGuidanceUiState.Hidden -> Unit

            is BreachDirectionalGuidanceUiState.Unavailable -> {
                Text(
                    text = state.message,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag(BREACH_GUIDANCE_UNAVAILABLE_TEST_TAG),
                )
            }

            is BreachDirectionalGuidanceUiState.FloatingArrow -> {
                Icon(
                    imageVector = Icons.Outlined.Navigation,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-96).dp)
                        .rotate(state.bearingDegrees.toFloat())
                        .testTag(BREACH_GUIDANCE_FLOATING_ARROW_TEST_TAG),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            is BreachDirectionalGuidanceUiState.OnTarget -> {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag(BREACH_GUIDANCE_ON_TARGET_TEST_TAG),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
```

```kotlin
private fun SignalPanel(
    state: BreachProtocolUiState.SignalLocked,
    dispatch: (MapIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    PanelSurface(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = state.districtName,
                style = MaterialTheme.typography.titleMedium,
            )
            state.distanceMeters?.let { distanceMeters ->
                Text(
                    text = stringResource(R.string.breach_distance_meters, distanceMeters),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.disabledReason != null) {
                Text(
                    text = state.disabledReason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { dispatch(MapIntent.StartBreachUpload) },
                    enabled = state.canStartUpload,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.breach_start_upload))
                }
                OutlinedButton(
                    onClick = { dispatch(MapIntent.DismissBreachPanel) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.breach_dismiss_button))
                }
            }
        }
    }
}
```

```kotlin
MapContent(
    state = state,
    dispatch = dispatch,
    mapContent = { modifier, mapDispatch ->
        MapLibreViewMapScreen(
            modifier = modifier,
            mapMode = state.mapMode,
            // existing callbacks
        )
    },
)

BreachProtocolOverlay(
    state = state.breachProtocol,
    guidance = state.breachGuidance,
    dispatch = dispatch,
    modifier = Modifier.fillMaxSize(),
)
```

```kotlin
private fun contentState(
    isStartupSplashVisible: Boolean,
    breachProtocol: BreachProtocolUiState = BreachProtocolUiState.Idle,
    breachGuidance: BreachDirectionalGuidanceUiState = BreachDirectionalGuidanceUiState.Hidden,
): MapUiState.Content =
    MapUiState.Content(
        northResetRequestToken = 0,
        isExplorationTrackingActive = true,
        explorationTrackingCadence = ExplorationTrackingCadence.FOREGROUND,
        explorationTrackingStatus = ExplorationTrackingStatus.TRACKING,
        breachProtocol = breachProtocol,
        breachGuidance = breachGuidance,
        isStartupSplashVisible = isStartupSplashVisible,
        startupSplashMessage = R.string.map_view_startup_loading_message,
        mapMode = MapMode.Exploration(
            styleUri = "asset://map/styles/light.json",
        ),
        mapStyleUri = "asset://map/styles/cyberpunk.json",
        visibleObjects = emptyList(),
        fogOfWar = FogOfWarUiState(),
    )
```

- [ ] **Step 4: Run verification for production and test code**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: PASS with the new overlay, UI state, and strings compiling.

Run: `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.MapViewModelTest"`
Expected: PASS with guidance-state assertions green.

Run: `./gradlew :app:assembleDebugAndroidTest`
Expected: PASS with the Compose UI tests compiling against the new HUD and panel API.

- [ ] **Step 5: Commit the rendered guidance HUD**

```bash
git add app/src/main/java/com/github/arhor/journey/feature/map/BreachProtocolOverlay.kt \
  app/src/main/java/com/github/arhor/journey/feature/map/MapScreen.kt \
  app/src/main/res/values/strings.xml \
  app/src/androidTest/java/com/github/arhor/journey/feature/map/MapScreenTest.kt
git commit -m "feat: render breach directional guidance hud"
```

## Self-Review Checklist

- Spec coverage:
  - Exact-bearing floating arrow: covered by Tasks 1, 2, and 4.
  - Collapse into on-target marker in upload range: covered by Tasks 2, 3, and 4.
  - Tactical map mode remains active during lookup: preserved in Task 3 via existing `mapMode` assertions.
  - No path/route rendering: enforced by Task 4's overlay-only implementation.
  - Location-unavailable fallback: covered by Tasks 2, 3, and 4.
- Placeholder scan:
  - No `TODO`, `TBD`, or cross-task "same as above" shortcuts remain.
- Type consistency:
  - `BreachDirectionalGuidanceUiState` names are consistent across presenter, view model, and UI tests.
  - `BreachProtocolUiState.SignalLocked` consistently drops `signalStrengthPercent` everywhere after Task 3.
