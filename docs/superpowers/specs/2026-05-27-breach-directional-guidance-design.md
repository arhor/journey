# Breach Directional Guidance Design

## Goal

Replace the current breach lookup "signal strength" interaction with a directional guidance mechanic that feels like
tracking a hidden target rather than reading a utility panel.

The player should not see a route or build path. Instead, breach lookup should provide exact direction only: a floating
arrow that points toward the locked breach while tactical breach mode keeps the camera centered on the player. When the
player reaches upload range, that floating arrow should collapse into an on-target marker over the player's location to
signal that the search phase is complete and the upload phase can begin.

## Relationship To Existing Specs

This design extends, rather than replaces:

- `docs/superpowers/specs/2026-05-14-breach-protocol-design.md`
- `docs/superpowers/specs/2026-05-27-breach-search-tactical-mode-design.md`

The earlier specs define the breach session lifecycle and the broader tactical map mode. This document narrows one part
of that behavior: how `SignalLocked` breach lookup should guide the player toward the breach.

## Approved Product Decisions

- The directional cue is a HUD overlay, not a world-space map marker or route.
- The cue uses exact bearing to the locked breach at all times.
- During tracking, the cue is a floating arrow positioned a fixed distance ahead of the player's screen anchor.
- When the player enters upload range, the floating arrow collapses into an on-target marker over the player's screen
  anchor.
- Breach lookup keeps the tactical map mode active: player-centered follow remains on, free pan remains disabled, and
  the map behaves like a focused tracking mode rather than a normal exploration map.
- Supporting text such as district name and optional distance remains secondary to the directional cue.

## Problem Statement

The current `SignalLocked` experience is primarily a bottom panel with signal strength percentage and distance text.
That communicates state, but it does not create a satisfying search loop. The player is reading numbers instead of
following a direction cue through space.

The desired behavior changes the interaction model:

- from "read signal strength and infer where to go";
- to "move through the world while the HUD points toward the breach."

The breach remains hidden in the sense that the player still has no route, path, or breadcrumb trail. They only get a
direction and must physically navigate toward it.

## Design Decision

Represent breach lookup guidance as a dedicated UI state derived from:

- the locked breach definition;
- the player's latest tracked location;
- the breach interaction radius.

Render that guidance in a fullscreen Compose HUD layer above the map surface, separate from the compact bottom action
panel.

Do not render the directional cue as a MapLibre annotation, polyline, or map object. The mechanic is intentionally a
screen-space guidance system, not a route-building or world-labeling system.

## User Experience

### Idle

- The existing pulse action remains the entry point.
- No directional cue is shown.
- The map stays in normal exploration mode until breach lookup starts.

### Scanning

- The map enters breach tactical mode immediately after pulse.
- The UI may briefly show a scanning state while the nearest breach is resolved.
- No route or target marker is exposed during this transient phase.

### Tracking

- Once a breach is locked, the UI shows a directional HUD instead of making the signal panel the primary mechanic.
- A floating arrow appears a fixed distance ahead of the player's screen anchor.
- The arrow rotates to the exact bearing from the player's current location to the breach location.
- The compact bottom panel shows supporting content only:
  - district name;
  - short status text;
  - optional distance text;
  - dismiss action;
  - upload action, disabled until the player reaches range.

### In Upload Range

- When the player reaches the breach interaction radius, the directional HUD changes state.
- The floating arrow collapses to the player's screen anchor and becomes an on-target marker.
- This collapse is the primary confirmation that the search phase is over.
- The upload action becomes enabled immediately.

### Uploading And Completed

- Uploading and completion continue to use the tactical breach mode defined by the earlier specs.
- The directional tracking cue is no longer needed once upload begins.
- The on-target marker may remain briefly during the pre-upload state, but directional floating guidance should not
  continue during upload progress.

## State Model

Keep `BreachProtocolUiState` as the authoritative breach session model, but split directional presentation into a
focused guidance model owned by the map feature.

The exact type name can be chosen during implementation, but the model should represent:

- `Hidden`: no directional HUD is visible.
- `Unavailable`: breach lookup is active but location is unavailable, so direction cannot be computed.
- `FloatingArrow`:
  - breach node id;
  - district name if needed by UI composition;
  - bearing in degrees from player to breach;
  - current distance in meters;
  - fixed screen offset policy or display token;
  - upload availability flag.
- `OnTarget`:
  - breach node id;
  - current distance in meters if still useful for UI copy;
  - upload availability flag, expected to be true.

This guidance model belongs in `MapUiState.Content` or inside the breach protocol content exposed to the screen. It
should not be reconstructed ad hoc inside composables.

## Data Flow

### Source Of Truth

`MapViewModel` remains the owner of:

- the locked breach;
- the active breach phase;
- the latest tracked player location;
- the effective tactical or exploration map mode.

### Guidance Derivation

Directional guidance should be derived from player location and breach location, not from camera heading, viewport
bounds, or map center. This keeps the gameplay mechanic stable even if tactical map rendering details change.

The derivation flow is:

1. `PulseClicked` resolves the nearest breach and locks it into session state.
2. The view model recomputes guidance whenever either the locked breach or tracked player location changes.
3. A small presenter or policy class maps raw distance and bearing into a UI-facing guidance state.
4. The screen renders the guidance state in the directional HUD layer and the supporting controls in the bottom panel.

### Bearing Calculation

The bearing should be the exact geodesic direction from the player's current location to the breach location. No
intentional jitter, wobble, or imprecision should be added.

### Range Transition

The threshold between `FloatingArrow` and `OnTarget` is the breach definition's existing interaction radius. There
should be a single clear transition:

- outside radius: `FloatingArrow`;
- inside radius: `OnTarget`.

Do not introduce additional intermediate states such as warm/cold, coarse/fine, or fuzzy signal zones in this
iteration.

## Rendering Strategy

### Directional HUD Layer

Add a dedicated fullscreen overlay composable above the map surface and alongside the existing breach overlay
infrastructure.

This layer should:

- place the directional cue relative to the player's screen anchor;
- use a deterministic fixed forward offset while in `FloatingArrow`;
- use zero offset while in `OnTarget`;
- rotate the cue using the bearing provided by UI state;
- avoid depending on MapLibre annotation APIs.

The fixed offset should be large enough to read clearly without drifting near screen edges in common phone sizes. The
final pixel or dp value can be tuned during implementation, but it should be treated as a UI constant rather than
derived from map zoom or real-world distance.

### Bottom Control Panel

Retain a compact breach panel for actions and textual status, but demote it from "signal instrument" to "supporting
controls."

The panel should include only what helps the player act:

- district name;
- short tracking or availability message;
- optional distance text;
- start upload action;
- dismiss or cancel action.

Signal strength percentage should be removed from the primary lookup experience. If distance remains, it should be
supporting text rather than the main feedback loop.

## Location-Unavailable Behavior

If breach lookup is active and tracked location becomes unavailable:

- keep breach tactical mode active;
- hide the directional arrow itself, because its bearing would no longer be trustworthy;
- show an unavailable guidance state in the overlay or panel;
- disable upload-start affordances until location returns and range can be recomputed.

Do not automatically dismiss breach lookup or restore exploration mode because of transient location loss.

## Map Mode Integration

This guidance mechanic depends on the tactical map mode defined in
`2026-05-27-breach-search-tactical-mode-design.md`.

During breach lookup and upload:

- the map remains player-centered;
- free pan remains disabled;
- player-centered drag controls remain available if already approved by the tactical mode design;
- normal exploration gestures are restored only when the breach flow is dismissed back to idle.

The directional HUD is therefore complementary to tactical mode, not a replacement for it.

## Error Handling

### No Breach Found

If pulse does not find a breach, the flow should continue to dismiss back to idle as it does now unless product
requirements later introduce a dedicated "nothing in range" state.

### Missing Location

If location is missing before or during tracking:

- do not fabricate direction;
- surface a clear "location required" message;
- keep the breach session alive if a breach is already locked.

### Upload Start Validation

Starting upload should continue to validate against real distance and location availability in the view model. The
guidance HUD is informative, not authoritative.

## Testing Strategy

### JVM ViewModel Tests

Add or update focused tests to verify:

- locking a breach with available location emits `FloatingArrow` guidance;
- entering the interaction radius emits `OnTarget` guidance;
- losing location during a locked breach emits unavailable guidance without exiting tactical mode;
- dismissing breach lookup clears guidance and restores exploration mode;
- upload remains disabled outside radius and becomes enabled inside radius.

### Presenter Or Policy Tests

If guidance derivation is extracted into a dedicated presenter or policy class, test:

- exact bearing mapping for representative coordinates;
- threshold transitions at, below, and above interaction radius;
- missing-location fallback behavior.

### Compose UI Tests

Add narrow UI tests for:

- floating arrow visibility during locked breach tracking;
- on-target marker visibility when upload is available;
- unavailable-state messaging when guidance cannot be computed.

Because the cue is a Compose overlay, these tests should not require full MapLibre instrumentation to validate the core
behavior.

## Tradeoffs

### Why HUD Instead Of World Marker

A HUD cue is less "physically placed" than a true map annotation, but it better serves the product goal. It reinforces
direction without implying a visible destination, route, or map pin that would weaken the sense of searching.

### Why Exact Bearing

Exact bearing is simpler, clearer, and more deterministic than simulated signal noise. It also makes the feature easier
to test and tune. If the search loop later proves too easy, difficulty should be adjusted through scan range, breach
density, or upload constraints rather than artificial bearing jitter.

### Why Keep Supporting Distance Text

Distance text can remain useful as confirmation and accessibility support, but it should no longer carry the gameplay
experience by itself.

## Out Of Scope

- adding pathfinding or route drawing;
- adding edge-of-screen fallback indicators;
- adding gyroscope or AR aiming;
- introducing intentional bearing fuzziness or hot/cold minigame logic;
- reworking breach upload rules;
- changing breach selection rules in the domain layer beyond what is needed to expose directional guidance state.
