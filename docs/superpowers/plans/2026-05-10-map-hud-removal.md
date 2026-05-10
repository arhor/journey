# Map HUD Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the existing map screen HUD feature so the map screen starts from a clean map-first surface.

**Architecture:** Remove the HUD data path from `MapRoute`, remove HUD parameters and rendering from `MapScreen`, then delete the now-unused HUD UI/state/ViewModel/resources/tests. Keep the map startup splash and all MapLibre callback plumbing unchanged.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Android instrumentation tests, Gradle.

---

## File Structure

Modify:

- `app/src/main/java/com/github/arhor/journey/feature/map/MapRoute.kt`: stop injecting/collecting `MapHudViewModel`; keep map state/effects and destination callbacks.
- `app/src/main/java/com/github/arhor/journey/feature/map/MapScreen.kt`: remove HUD parameters and `MapPlayerHud` rendering; keep startup splash.
- `app/src/androidTest/java/com/github/arhor/journey/feature/map/MapScreenTest.kt`: remove HUD fixtures and HUD interaction test; update remaining `MapContent` calls.
- `app/src/main/res/values/strings.xml`: remove HUD-only string resources.

Delete:

- `app/src/main/java/com/github/arhor/journey/feature/map/MapPlayerHud.kt`
- `app/src/main/java/com/github/arhor/journey/feature/map/MapHudViewModel.kt`
- `app/src/main/java/com/github/arhor/journey/feature/map/MapHudUiState.kt`
- `app/src/androidTest/java/com/github/arhor/journey/feature/map/MapPlayerHudTest.kt`
- `app/src/test/java/com/github/arhor/journey/feature/map/MapHudViewModelTest.kt`

Do not modify:

- `MapViewModel`, `MapUiState`, fog-of-war, MapLibre interop, watchtower UI, or hero feature code.
- `MapIntent`; existing map intents are outside the HUD removal scope.

---

### Task 1: Remove HUD From Map Screen API

**Files:**
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapRoute.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/MapScreen.kt`
- Modify: `app/src/androidTest/java/com/github/arhor/journey/feature/map/MapScreenTest.kt`

- [ ] **Step 1: Update `MapScreenTest` first**

In `app/src/androidTest/java/com/github/arhor/journey/feature/map/MapScreenTest.kt`, remove these imports:

```kotlin
import androidx.compose.ui.test.performClick
import com.github.arhor.journey.core.common.ResourceType
```

Remove this test entirely:

```kotlin
@Test
fun `MapContent should block HUD interaction while startup splash overlay is visible`() {
    // Given
    var heroClicks = 0
    composeRule.setContent {
        MaterialTheme {
            MapContent(
                state = contentState(isStartupSplashVisible = true),
                hudState = hudState(),
                dispatch = {},
                onOpenHero = { heroClicks += 1 },
                onOpenSettings = {},
                mapContent = { modifier, _ -> Box(modifier = modifier) },
            )
        }
    }

    // When
    composeRule.onNodeWithTag(MAP_HUD_HERO_BUTTON_TEST_TAG).performClick()

    // Then
    heroClicks shouldBe 0
}
```

In both remaining `MapContent(...)` calls, delete these arguments:

```kotlin
hudState = hudState(),
onOpenHero = {},
onOpenSettings = {},
```

Delete the `hudState()` helper at the bottom of the file:

```kotlin
private fun hudState(): MapHudUiState.Content =
    MapHudUiState.Content(
        heroInitial = "A",
        levelLabel = "Lv 7",
        xpInLevel = 4_850,
        xpToNextLevel = 7_500,
        resources = listOf(
            MapHudResourceUiModel(
                resourceType = ResourceType.SCRAP,
                amount = 1_250,
                amountLabel = "1.2K",
            ),
            MapHudResourceUiModel(
                resourceType = ResourceType.COMPONENTS,
                amount = 12_300,
                amountLabel = "12K",
            ),
            MapHudResourceUiModel(
                resourceType = ResourceType.FUEL,
                amount = 1_300_000,
                amountLabel = "1.3M",
            ),
        ),
    )
```

After this step, the file should still contain the two startup splash tests and the `contentState(...)` helper.

- [ ] **Step 2: Run the focused Android test compilation to verify the expected failure**

Run:

```shell
./gradlew :app:assembleDebugAndroidTest
```

Expected: FAIL because `MapContent` still requires `hudState`, `onOpenHero`, and `onOpenSettings`.

- [ ] **Step 3: Update `MapRoute`**

In `app/src/main/java/com/github/arhor/journey/feature/map/MapRoute.kt`, keep this import because `MapViewModel`
state collection still uses it:

```kotlin
import androidx.lifecycle.compose.collectAsStateWithLifecycle
```

Remove only the HUD ViewModel parameter from `MapRoute`:

```kotlin
hudVm: MapHudViewModel = hiltViewModel(),
```

Remove HUD state collection:

```kotlin
val hudState by hudVm.uiState.collectAsStateWithLifecycle()
```

Update the `MapScreen(...)` call so it no longer passes HUD state or destination callbacks:

```kotlin
MapScreen(
    state = state,
    dispatch = vm::dispatch,
)
```

Keep `onOpenHero` and `onOpenSettings` parameters on `MapRoute` for now, because app navigation still passes them into the route. Suppress unused callback warnings by leaving them as named parameters; Kotlin does not require local use for public Composable parameters.

- [ ] **Step 4: Update `MapScreen` signatures and rendering**

In `app/src/main/java/com/github/arhor/journey/feature/map/MapScreen.kt`, remove unused imports after deleting HUD rendering:

```kotlin
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
```

Change `MapScreen` from:

```kotlin
fun MapScreen(
    state: MapUiState,
    hudState: MapHudUiState,
    dispatch: (MapIntent) -> Unit,
    onOpenHero: () -> Unit,
    onOpenSettings: () -> Unit,
)
```

to:

```kotlin
fun MapScreen(
    state: MapUiState,
    dispatch: (MapIntent) -> Unit,
)
```

Change the `MapContent(...)` call inside `MapScreen` from:

```kotlin
MapContent(
    state = state,
    hudState = hudState,
    dispatch = dispatch,
    onOpenHero = onOpenHero,
    onOpenSettings = onOpenSettings,
)
```

to:

```kotlin
MapContent(
    state = state,
    dispatch = dispatch,
)
```

Change `MapContent` from:

```kotlin
internal fun MapContent(
    state: MapUiState.Content,
    hudState: MapHudUiState,
    dispatch: (MapIntent) -> Unit,
    onOpenHero: () -> Unit,
    onOpenSettings: () -> Unit,
    mapContent: @Composable (Modifier, (MapIntent) -> Unit) -> Unit = { modifier, mapDispatch ->
```

to:

```kotlin
internal fun MapContent(
    state: MapUiState.Content,
    dispatch: (MapIntent) -> Unit,
    mapContent: @Composable (Modifier, (MapIntent) -> Unit) -> Unit = { modifier, mapDispatch ->
```

Delete this block from the `Box` in `MapContent`:

```kotlin
MapPlayerHud(
    state = hudState,
    onHeroClick = onOpenHero,
    onSettingsClick = onOpenSettings,
    modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(horizontal = 16.dp, vertical = 10.dp),
)
```

- [ ] **Step 5: Re-run focused Android test compilation**

Run:

```shell
./gradlew :app:assembleDebugAndroidTest
```

Expected: PASS for Android test source compilation.

- [ ] **Step 6: Commit Task 1**

```shell
git add app/src/main/java/com/github/arhor/journey/feature/map/MapRoute.kt app/src/main/java/com/github/arhor/journey/feature/map/MapScreen.kt app/src/androidTest/java/com/github/arhor/journey/feature/map/MapScreenTest.kt
git commit -m "Remove map HUD from screen surface"
```

---

### Task 2: Delete HUD Feature Code And Resources

**Files:**
- Delete: `app/src/main/java/com/github/arhor/journey/feature/map/MapPlayerHud.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/feature/map/MapHudViewModel.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/feature/map/MapHudUiState.kt`
- Delete: `app/src/androidTest/java/com/github/arhor/journey/feature/map/MapPlayerHudTest.kt`
- Delete: `app/src/test/java/com/github/arhor/journey/feature/map/MapHudViewModelTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Delete HUD production and test files**

Delete these files:

```text
app/src/main/java/com/github/arhor/journey/feature/map/MapPlayerHud.kt
app/src/main/java/com/github/arhor/journey/feature/map/MapHudViewModel.kt
app/src/main/java/com/github/arhor/journey/feature/map/MapHudUiState.kt
app/src/androidTest/java/com/github/arhor/journey/feature/map/MapPlayerHudTest.kt
app/src/test/java/com/github/arhor/journey/feature/map/MapHudViewModelTest.kt
```

- [ ] **Step 2: Remove HUD strings**

In `app/src/main/res/values/strings.xml`, remove these exact string entries:

```xml
<string name="map_hud_hero_content_description">Open hero screen, %1$s</string>
<string name="map_hud_settings_content_description">Open settings</string>
<string name="map_hud_resource_content_description">%1$s: %2$d</string>
<string name="map_hud_weather_title">Weather</string>
<string name="map_hud_weather_condition">Light rain</string>
<string name="map_hud_weather_details">18°C / SW 12 km/h</string>
<string name="map_hud_xp_label">XP %1$d / %2$d</string>
<string name="map_weather_content_description">Weather conditions</string>
```

- [ ] **Step 3: Search for stale HUD references**

Run:

```shell
rg -n "MapHud|MapPlayerHud|MAP_HUD|mapHud|map_hud|map_weather|formatResourceAmount|toMapHudUiState" app/src/main/java app/src/test/java app/src/androidTest/java app/src/main/res
```

Expected: no output.

- [ ] **Step 4: Compile production Kotlin**

Run:

```shell
./gradlew :app:compileDebugKotlin -q
```

Expected: PASS.

- [ ] **Step 5: Assemble Android tests**

Run:

```shell
./gradlew :app:assembleDebugAndroidTest
```

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

```shell
git add -A app/src/main/java/com/github/arhor/journey/feature/map app/src/androidTest/java/com/github/arhor/journey/feature/map app/src/test/java/com/github/arhor/journey/feature/map app/src/main/res/values/strings.xml
git commit -m "Delete old map HUD feature"
```

---

### Task 3: Final Verification

**Files:**
- Existing files changed by Tasks 1 and 2.

- [ ] **Step 1: Verify final HUD absence**

Run:

```shell
rg -n "MapHud|MapPlayerHud|MAP_HUD|mapHud|map_hud|map_weather|formatResourceAmount|toMapHudUiState" app/src/main/java app/src/test/java app/src/androidTest/java app/src/main/res
```

Expected: no output.

- [ ] **Step 2: Verify production and Android test compilation**

Run:

```shell
./gradlew :app:compileDebugKotlin -q
./gradlew :app:assembleDebugAndroidTest
```

Expected: both commands exit 0.

- [ ] **Step 3: Inspect change scope**

Run:

```shell
git status --short
git diff --stat HEAD~2..HEAD
```

Expected: only map HUD removal files, `MapRoute`, `MapScreen`, `MapScreenTest`, and `strings.xml` changed after the spec/plan docs.
