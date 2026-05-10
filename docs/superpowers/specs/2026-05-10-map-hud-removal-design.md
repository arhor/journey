# Map HUD Removal Design

## Goal

Remove the existing map screen HUD feature so the map screen can be redesigned from a clean surface.

This removes the current player/status HUD rather than hiding it. The resulting map screen should render the map,
map startup splash, and existing map-owned overlays only. It should no longer collect hero/resource data just to feed
the removed HUD.

## Current HUD Elements

The HUD feature currently includes:

- Top player/status panel rendered by `MapPlayerHud`.
- Hero level shield button that opens the hero screen.
- Settings icon button that opens settings.
- XP strip.
- Compact resource chips for scrap, components, and fuel.
- Static weather card.
- `MapHudViewModel`, `MapHudUiState`, `MapHudResourceUiModel`, and resource amount formatting.
- HUD-specific strings and tests.

The startup splash overlay in `MapScreen` is not part of the HUD feature. It remains because it blocks interaction
while the MapLibre surface and first location fix are starting.

## Decision

Delete the whole HUD feature and its direct tests.

`MapRoute` should stop creating `MapHudViewModel` and stop collecting `hudState`. `MapScreen` and `MapContent` should
stop accepting HUD state and should not render `MapPlayerHud`. The map content area remains full-screen and continues
to report viewport size and MapLibre callbacks through `MapIntent`.

The route can keep `onOpenHero` and `onOpenSettings` parameters only if upstream navigation still passes them through
the map destination. They should not be invoked from the map screen after this removal. A later HUD redesign can decide
where those actions belong.

## Removal Scope

Remove these production files if they have no remaining non-HUD consumers:

- `app/src/main/java/com/github/arhor/journey/feature/map/MapPlayerHud.kt`
- `app/src/main/java/com/github/arhor/journey/feature/map/MapHudViewModel.kt`
- `app/src/main/java/com/github/arhor/journey/feature/map/MapHudUiState.kt`

Remove HUD-only string resources:

- `map_hud_hero_content_description`
- `map_hud_settings_content_description`
- `map_hud_resource_content_description`
- `map_hud_weather_title`
- `map_hud_weather_condition`
- `map_hud_weather_details`
- `map_hud_xp_label`
- `map_weather_content_description`

Remove these tests because they cover deleted behavior:

- `app/src/androidTest/java/com/github/arhor/journey/feature/map/MapPlayerHudTest.kt`
- `app/src/test/java/com/github/arhor/journey/feature/map/MapHudViewModelTest.kt`

Update `MapScreenTest` so it covers the startup splash without constructing HUD state or asserting HUD interaction.
The previous "startup splash blocks HUD interaction" test should be removed because there is no HUD interaction left
to block.

## Preserved Behavior

Keep these map behaviors unchanged:

- Loading and error states in `MapScreen`.
- Full-screen `MapLibreViewMapScreen` rendering.
- Viewport size reporting through `MapIntent.MapViewportSizeChanged`.
- Camera, location, map load, and startup gate callbacks.
- Startup splash visibility and message behavior.
- Fog-of-war render state.
- Any existing map object, watchtower, and MapLibre interop behavior outside the HUD files.

## Architecture Impact

The map feature will have one fewer ViewModel dependency. Hero and resource observation remains owned by hero-related
screens and use cases, not by the map route. This reduces map screen startup work and removes stale UI concepts before
the next HUD design.

No new abstraction replaces the HUD in this step. The desired end state is absence of the old feature, not an empty
placeholder component.

## Testing

Run the narrowest checks that prove removal is clean:

```shell
./gradlew :app:compileDebugKotlin -q
./gradlew :app:assembleDebugAndroidTest
```

If `MapScreenTest` is updated and a focused instrumentation run is available in the environment, run:

```shell
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.github.arhor.journey.feature.map.MapScreenTest
```

The implementation is complete when the map screen compiles without HUD types, HUD strings have no references, and
the remaining map screen tests do not construct or interact with HUD state.
