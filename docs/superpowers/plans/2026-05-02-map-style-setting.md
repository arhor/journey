# Map Style Setting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a persisted Settings control that switches MapLibre between the three bundled map styles.

**Architecture:** `:core:domain` owns style catalog, repository contract, and use cases. `:data` persists the selected style id with Preferences DataStore. `:feature:settings` writes the preference and `:feature:map` observes the selected style URI through its existing MVI state.

**Tech Stack:** Kotlin, Coroutines Flow, Preferences DataStore, Hilt, Jetpack Compose Material3, MapLibre.

---

### Task 1: Domain Style Setting Boundary

**Files:**
- Create: `core/domain/src/main/kotlin/com/github/arhor/journey/domain/repository/AppSettingsRepository.kt`
- Create: `core/domain/src/main/kotlin/com/github/arhor/journey/domain/usecase/ObserveAvailableMapStylesUseCase.kt`
- Create: `core/domain/src/main/kotlin/com/github/arhor/journey/domain/usecase/ObserveSelectedMapStyleUseCase.kt`
- Create: `core/domain/src/main/kotlin/com/github/arhor/journey/domain/usecase/SetSelectedMapStyleUseCase.kt`
- Modify: `core/domain/src/main/kotlin/com/github/arhor/journey/domain/model/MapStyle.kt`
- Test: `core/domain/src/test/kotlin/com/github/arhor/journey/domain/model/MapStyleTest.kt`
- Test: `core/domain/src/test/kotlin/com/github/arhor/journey/domain/usecase/MapStyleSettingsUseCaseTest.kt`

- [ ] Write failing tests for bundled style constants, default style, available style observation, selected style observation, and setting a style id.
- [ ] Run `./gradlew :core:domain:test --tests '*MapStyle*' --tests '*MapStyleSettingsUseCaseTest'`; expect failures for missing APIs.
- [ ] Add `MapStyle.Companion` constants and helper lookup.
- [ ] Add repository contract and use cases returning `Output`/`Flow<Output<...>>`.
- [ ] Re-run `./gradlew :core:domain:test --tests '*MapStyle*' --tests '*MapStyleSettingsUseCaseTest'`; expect pass.

### Task 2: DataStore Repository Implementation

**Files:**
- Create: `data/src/main/kotlin/com/github/arhor/journey/data/repository/DataStoreAppSettingsRepository.kt`
- Create: `data/src/main/kotlin/com/github/arhor/journey/data/di/AppSettingsDataStoreModule.kt`
- Modify: `data/src/main/kotlin/com/github/arhor/journey/data/di/RepositoryModule.kt`
- Test: `data/src/test/kotlin/com/github/arhor/journey/data/repository/DataStoreAppSettingsRepositoryTest.kt`

- [ ] Write failing tests proving default fallback, valid saved id resolution, unknown id fallback, and write failure mapping.
- [ ] Run `./gradlew :data:testDebugUnitTest --tests '*DataStoreAppSettingsRepositoryTest'`; expect failure for missing implementation.
- [ ] Implement Preferences DataStore persistence for the selected map style id.
- [ ] Bind `AppSettingsRepository` in Hilt and provide a singleton `DataStore<Preferences>`.
- [ ] Re-run `./gradlew :data:testDebugUnitTest --tests '*DataStoreAppSettingsRepositoryTest'`; expect pass.

### Task 3: Settings UI and ViewModel

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/github/arhor/journey/feature/settings/SettingsIntent.kt`
- Modify: `feature/settings/src/main/kotlin/com/github/arhor/journey/feature/settings/SettingsUiState.kt`
- Modify: `feature/settings/src/main/kotlin/com/github/arhor/journey/feature/settings/SettingsViewModel.kt`
- Modify: `feature/settings/src/main/kotlin/com/github/arhor/journey/feature/settings/SettingsRoute.kt`
- Modify: `feature/settings/src/main/kotlin/com/github/arhor/journey/feature/settings/SettingsScreen.kt`
- Modify: `feature/settings/src/main/res/values/strings.xml`
- Test: `feature/settings/src/test/kotlin/com/github/arhor/journey/feature/settings/SettingsViewModelTest.kt`

- [ ] Replace the static ViewModel test with failing tests for rendering selected style state and dispatching a style selection.
- [ ] Run `./gradlew :feature:settings:testDebugUnitTest --tests '*SettingsViewModelTest'`; expect failures.
- [ ] Add content state fields for available styles and selected style id.
- [ ] Inject observe/set use cases into `SettingsViewModel` and map failures to `SettingsUiState.Failure` or snackbar effects.
- [ ] Add a Material3 radio-button list for map styles.
- [ ] Re-run `./gradlew :feature:settings:testDebugUnitTest --tests '*SettingsViewModelTest'`; expect pass.

### Task 4: Map Style Consumption

**Files:**
- Modify: `feature/map/src/main/kotlin/com/github/arhor/journey/feature/map/MapUiState.kt`
- Modify: `feature/map/src/main/kotlin/com/github/arhor/journey/feature/map/MapViewModel.kt`
- Modify: `feature/map/src/main/kotlin/com/github/arhor/journey/feature/map/MapScreen.kt`
- Modify: `feature/map/src/main/kotlin/com/github/arhor/journey/feature/map/viewinterop/MapLibreViewMapScreen.kt`
- Test: `feature/map/src/test/kotlin/com/github/arhor/journey/feature/map/MapViewModelTest.kt`
- Test: `feature/map/src/androidTest/kotlin/com/github/arhor/journey/feature/map/MapScreenTest.kt`

- [ ] Write failing test that `MapViewModel` exposes the selected style URI in `MapUiState.Content`.
- [ ] Update `MapScreenTest` fixture calls for the new state field.
- [ ] Run `./gradlew :feature:map:testDebugUnitTest --tests '*MapViewModelTest'`; expect failure for missing style state.
- [ ] Inject `ObserveSelectedMapStyleUseCase`, combine it into map UI state, and pass `styleUri` to MapLibre.
- [ ] Change `MapLibreViewMapScreen` and `LegacyMapLibreMap` to accept `styleUri`; key the Android view and set the style from that parameter.
- [ ] Re-run `./gradlew :feature:map:testDebugUnitTest --tests '*MapViewModelTest'`; expect pass.

### Task 5: Full Verification

**Files:**
- Existing Gradle modules touched above.

- [ ] Run `./gradlew :core:domain:test :data:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:map:testDebugUnitTest :feature:map:compileDebugKotlin --stacktrace`.
- [ ] Inspect `git diff --stat` and `git diff --cached --stat` to confirm staged style assets remain staged and implementation files are unstaged unless intentionally staged later.
