# Breach Search Tactical Mode Design

## Goal

Make breach search mode feel like a distinct tactical scanning state while keeping default exploration light, normal,
and map-first.

## Approved Product Decisions

- Normal exploration always uses the light map style.
- Breach search mode swaps the map into the cyberpunk style.
- Breach search mode keeps zoom enabled.
- If location becomes unavailable during breach search, the mode stays active and degrades gracefully instead of
  immediately exiting.

## Current Architecture Summary

### Map Rendering

- `MapScreen` renders `MapUiState.Content` and delegates the map surface to `MapLibreViewMapScreen`.
- `MapLibreViewMapScreen` owns the Android `MapView`, location component wiring, fog-of-war layer controller, map
  object controller, camera listeners, and custom gesture controller.
- The current implementation keys the Compose `AndroidView` by `styleUri`, so style changes recreate the `MapView`
  instead of updating style in place.

### Map Style Selection

- `MapViewModel` observes the persisted style via `ObserveSelectedMapStyleUseCase` and exposes the selected asset URI
  through `MapUiState.Content.mapStyleUri`.
- `DataStoreAppSettingsRepository` persists the selected style id and falls back to `MapStyle.defaultStyle` if nothing
  is stored or the id is unknown.
- `MapStyle.defaultStyle` is currently `cyberpunk`, which conflicts with the new requirement that normal exploration be
  light by default.

### Breach Search Flow

- `MapViewModel` owns breach session state with `BreachProtocolUiState` plus an internal `BreachSessionPhase`.
- `PulseClicked` transitions from `Idle` to `Scanning`, resolves the nearest breach node, and moves into
  `SignalLocked`.
- Uploading and completion stay inside `MapViewModel`, and `DismissBreachPanel` resets the breach session to `Idle`.

### User Location Tracking

- `ExplorationTrackingRuntime` owns app-level location tracking and exposes `ExplorationTrackingSession`.
- `MapViewModel` reads the latest tracked location from `trackingSession.value.lastKnownLocation`.
- `MapLibreViewMapScreen` also wires a `ForwardingLocationEngine` into MapLibre's location component to keep the map
  surface updated with native location events.

### Camera State And Gesture Handling

- The map currently starts in `CameraMode.TRACKING`.
- Built-in MapLibre pan/rotate/tilt gestures are disabled globally in `configureUiSettings`.
- `NativeCameraGestureController` intercepts one-finger drags and uses `PlayerCenteredCameraGestureTracker` to remap
  horizontal movement to bearing and vertical movement to tilt while keeping the camera centered on the user.
- During this custom gesture, the location component drops to `CameraMode.NONE`; when the gesture ends it restores
  tracking mode.

### UI State Ownership

- `MapViewModel` is the correct place to own breach search mode, map style intent, and map interaction mode because it
  already coordinates map UI, breach flow, and location-derived state.
- `MapLibreViewMapScreen` should remain a declarative view/interop layer that applies a supplied interaction profile to
  the existing `MapView`.

## Problem Statement

The app already behaves like a tactical player-centered map at all times:

- cyberpunk is the default style;
- pan is disabled globally;
- one-finger drags already rotate and tilt instead of panning;
- the map uses tracking camera mode by default.

That means breach search mode is not currently distinct. To satisfy the requested behavior, the map needs two explicit
interaction profiles:

1. normal exploration;
2. breach tactical scan.

## Design Decision

Introduce an explicit map interaction mode derived from breach session state and surfaced through `MapUiState.Content`.

The mode controls:

- active map style URI;
- camera follow policy;
- gesture policy;
- tactical fallback affordances when location is unavailable.

Do not persist breach tactical mode into app settings. Breach mode is session UI state, not a user preference.

## Interaction Profiles

### Normal Exploration

- Light map style.
- Standard MapLibre gestures enabled for pan, zoom, rotate, and tilt.
- No forced follow camera.
- Recenter remains explicit rather than always-on.

This restores a conventional exploration map and creates the contrast needed for breach mode to feel deliberate.

### Breach Tactical Scan

- Cyberpunk map style.
- Camera anchored to the latest tracked user location.
- Normal pan disabled.
- One-finger horizontal drag changes bearing.
- One-finger vertical drag changes tilt.
- Zoom gestures remain enabled.
- Tilt remains clamped to the supported `DEFAULT_CAMERA_MIN_TILT..DEFAULT_CAMERA_MAX_TILT` range.
- Follow remains smooth and immediate enough to feel responsive without adding extra animation jitter.

The current `PlayerCenteredCameraGestureTracker` already matches most of this behavior and should be reused rather than
replaced.

## Breach Session To Interaction Mode Mapping

- `BreachProtocolUiState.Idle` maps to normal exploration mode.
- `BreachProtocolUiState.Scanning` maps to breach tactical mode.
- `BreachProtocolUiState.SignalLocked` maps to breach tactical mode.
- `BreachProtocolUiState.Uploading` maps to breach tactical mode.
- `BreachProtocolUiState.Completed` stays in breach tactical mode until the user dismisses the breach panel.

Keeping `Completed` tactical until dismissal makes the completion state feel intentional and gives the UI a single
explicit teardown point.

## Restoration Rules

Leaving breach mode must always:

- restore the light style;
- restore standard map gestures;
- disable forced follow;
- clear tactical-only interaction flags;
- keep fog-of-war and visible object rendering intact.

This restoration must hold after:

- recomposition;
- lifecycle pause/resume;
- navigation away/back while the `ViewModel` is retained;
- configuration changes that rebuild the map surface.

## Location-Unavailable Behavior

If tracked location becomes unavailable while breach tactical mode is active:

- keep the cyberpunk style active;
- keep breach tactical gesture policy active;
- suspend hard follow until location updates resume;
- keep the locked breach UI visible;
- disable location-dependent upload/start affordances until range can be recomputed safely.

Do not abruptly fall back to normal exploration mode for transient GPS loss.

## Map Style Handling

Update the effective style in the interop layer without recreating the `MapView`.

Reasoning:

- recreating the `MapView` on every style swap is heavier than necessary;
- it risks listener/controller churn during breach mode entry and exit;
- it makes camera continuity and lifecycle safety harder;
- it increases the chance of visible jitter or state loss.

The map interop should:

- retain a single `MapView` instance for the composable lifetime;
- call `setStyle(...)` only when the requested style URI actually changes;
- reattach fog-of-war, object layers, location component, and gesture policy after the new style loads;
- avoid redundant style reloads when the effective mode has not changed.

## Camera And Gesture Application Strategy

Treat map interaction policy as declarative state passed from `MapViewModel` into `MapLibreViewMapScreen`.

The interop layer should apply one of two profiles:

- `Exploration`: built-in MapLibre gestures on, no custom player-centered drag override, no forced tracking mode.
- `BreachTactical`: built-in pan off, zoom on, custom player-centered drag override on, follow anchored to latest
  location.

This reuses the current custom tactical controller for breach mode while allowing normal exploration to rely on the
native SDK gesture stack.

## Required Model Changes

Add a focused UI-facing configuration model instead of spreading behavior across booleans. The exact type name can be
chosen during implementation, but it should encode at least:

- interaction mode or gesture profile;
- effective map style URI;
- whether follow-to-user is active;
- whether breach actions are temporarily disabled by missing location.

This configuration belongs in map feature state, not in unrelated UI components and not in persisted settings.

## Testing Strategy

### ViewModel Tests

Extend `MapViewModelTest` to verify:

- normal exploration exposes the light style by default;
- pulse/scanning transitions switch the effective map mode to breach tactical;
- dismissing or canceling breach mode restores normal exploration mode;
- completed breach mode remains tactical until dismiss;
- missing location during breach mode keeps tactical state active but disables location-dependent affordances.

### Gesture Tests

Keep focused unit coverage around `PlayerCenteredCameraGestureTracker` and add tests only if the new profile split
changes tracker entry/exit behavior.

### Interop Tests

Add narrow tests around the map interop/controllers where practical to verify:

- style changes do not require `MapView` recreation;
- gesture policy flips correctly between exploration and breach tactical mode;
- tactical cleanup restores normal gesture behavior.

Prefer JVM coverage around extracted controller logic before adding heavier instrumentation tests.

## Tradeoffs

### Reworking Normal Mode

This change is broader than just "modify breach search" because the current default map already matches tactical
behavior. Reworking normal exploration is necessary to make breach search distinct and to satisfy the requirement that
normal map interaction is restored after breach mode ends.

### Zoom Policy

Keeping zoom enabled in breach mode preserves map-first usability and does not weaken the constraint that the player
cannot pan away from their location.

### Style Replaceability

The cyberpunk style remains replaceable because the mode selects through the existing map style mechanism instead of
hardcoding style logic deep inside map UI widgets.

## Out Of Scope

- redesigning the breach domain loop itself;
- adding new online-only style assets;
- altering fog-of-war algorithms;
- introducing a new minigame or gyroscope interaction;
- persisting breach tactical mode as a user preference.
