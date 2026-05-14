# Breach Protocol Epic Design

## Goal

Replace the watchtower area-control feature with Breach Protocol, a cyberpunk/noir gameplay loop where the player scans
for vulnerable infrastructure, physically approaches the signal, completes an upload while staying in range, and gains
permanent fog-of-war visibility for the controlled sector.

This is an epic, not a single small feature. The work should be split into larger tickets that each leave the app in a
coherent, buildable state, with smaller implementation sub-tasks inside each ticket.

## Design Decision

Remove watchtowers instead of renaming them.

Watchtowers currently provide deterministic map objects, discovery, claim, upgrade, map markers, bottom sheets, and
persistent fog reveal. Breach Protocol should start with its own domain language and storage so old concepts such as
"tower level", "claim cost", and "upgrade radius" do not shape the new mechanic.

The only concepts worth preserving are generic infrastructure patterns:

- deterministic location generation from map tiles;
- generated object caching by map area;
- Room-backed local state for player progress;
- location-driven interaction checks;
- fog-of-war reveal from controlled world objects.

Those patterns can be reused, but names and behavior should be breach-specific.

## Gameplay Scope

The first playable version covers the core Breach Protocol loop:

1. Player taps a Pulse action on the map.
2. The app finds the nearest uncontrolled breach node within scan range.
3. The map enters a scan/locked state and shows signal feedback instead of an immediate waypoint.
4. Signal strength updates as the player's tracked location changes.
5. When the player reaches the breach radius, the breach node becomes interactable.
6. The player starts an upload and must remain in radius until progress reaches 100%.
7. Completion marks the node controlled and permanently reveals its sector.

The first version intentionally excludes:

- gyroscope or data-slider minigame;
- audio layers;
- lockdown timers;
- passive income;
- movement buffs;
- multi-faction ownership;
- network synchronization;
- animated wireframe map style swapping.

Those can be follow-up tickets after the domain loop and map integration are stable.

## Domain Model

Introduce breach-specific models under `domain/model`:

- `BreachNodeDefinition`
    - `id`
    - `districtName`
    - `description`
    - `location`
    - `interactionRadiusMeters`
    - `revealRadiusMeters`
- `BreachNodeState`
    - `breachNodeId`
    - `discoveredAt`
    - `controlledAt`
    - `lockdownUntil`
    - `updatedAt`
- `BreachNodeRecord`
    - `definition`
    - nullable `state`
- `BreachNode`
    - presented domain object with phase, distance, and interaction flags.
- `ControlledBreachRevealSnapshot`
    - tiles permanently visible because controlled breach nodes cover them.

Initial phases:

- `UNDETECTED`: generated node with no persisted state.
- `SIGNAL_LOCKED`: scan has selected this node for the active session.
- `DISCOVERED`: player is close enough for the node to materialize.
- `CONTROLLED`: upload completed and sector reveal is permanent.
- `LOCKDOWN`: reserved for the later failure mechanic.

Only persisted states need storage. `SIGNAL_LOCKED` can remain a `MapViewModel` session state for the first version.

## Generation And Storage

Create a breach generator similar in spirit to the old deterministic object generation:

- generator version: `1`;
- generator tile zoom: start with the old map-object generator zoom unless tests show density is poor;
- stable id format: `breach-node:v1:<zoom>:<x>:<y>`;
- deterministic location inside the generator cell with padding away from tile edges;
- deterministic district-style names from a small adjective/noun vocabulary.

Create a new Room table:

```sql
CREATE TABLE IF NOT EXISTS `breach_node_state` (
    `breachNodeId` TEXT NOT NULL,
    `discoveredAt` INTEGER,
    `controlledAt` INTEGER,
    `lockdownUntil` INTEGER,
    `updatedAt` INTEGER NOT NULL,
    PRIMARY KEY(`breachNodeId`)
)
```

Add a database migration from version `5` to `6` that drops `watchtower_state` and creates `breach_node_state`.

## Repository And Use Cases

Create a `BreachNodeRepository` with deterministic generated definitions plus Room-backed state:

- observe breach records in bounds;
- get breach records in bounds;
- get records intersecting tiles;
- get by id;
- upsert discovered state;
- mark controlled;
- set or clear lockdown when the later failure mechanic is implemented.

Initial use cases:

- `FindNearestBreachNodeUseCase`
    - input: actor location and scan range.
    - output: nearest uncontrolled and non-lockdown breach node.
- `DiscoverBreachNodeUseCase`
    - persists discovery once the player gets close enough.
- `CompleteBreachUseCase`
    - validates range, marks controlled, and returns the controlled state.
- `ObserveControlledBreachRevealTilesUseCase`
    - replaces claimed watchtower reveal logic for persistent fog.
- `ObserveVisibleBreachNodesUseCase`
    - returns discovered or controlled breach nodes in the active query window.

The initial scan should not spend hero energy unless a separate energy regeneration/spending story is added. The feature
spec can display "requires energy" later, but the current repository has no robust energy spending use case.

## Map Feature Integration

Update `MapViewModel` to remove watchtower dependencies and add a breach protocol session state:

- idle;
- scanning;
- signal locked;
- in range;
- uploading;
- completed.

Add map intents:

- `PulseClicked`;
- `BreachNodeTapped`;
- `StartBreachUpload`;
- `BreachUploadTick`;
- `CancelBreachUpload`;
- `DismissBreachPanel`.

Add `MapUiState.Content.breachProtocol` with:

- current phase;
- signal strength percent;
- distance text;
- locked node summary;
- upload progress percent;
- interaction enabled flags;
- user-facing disabled reason.

The map screen should render Breach Protocol as map-owned overlays:

- Pulse button floating over the map;
- signal strength panel while locked;
- breach terminal panel while uploading or complete.

This is not a return of the removed general HUD. It is feature UI owned by the map gameplay loop.

## Map Objects And Fog

Replace watchtower map object presentation with breach node presentation:

- add `MapObjectKind.BreachNode`;
- add breach marker state for discovered, upload-ready, controlled, and lockdown;
- remove watchtower marker state and bottom sheet.

Map object rendering still needs follow-up work because `MapUiState.visibleObjects` exists but the current MapLibre
interop does not render dynamic objects. The Breach Protocol epic should include a ticket that wires `visibleObjects`
into MapLibre or a Compose overlay marker layer before relying on marker taps.

Fog-of-war should use controlled breach nodes:

- remove `ObserveClaimedWatchtowerRevealTilesUseCase`;
- add `ObserveControlledBreachRevealTilesUseCase`;
- update `FogOfWarController` to observe the new use case;
- preserve the current behavior where controlled object tiles are merged into permanent visibility.

The current native fog layer path is commented out in MapLibre interop. The epic should include a separate ticket to
restore or replace that rendering path so the sector reveal is visible, not only computed.

## Removal Scope

Delete watchtower production code:

- domain models, errors, repository contract, balance, generation, mapping, and use cases;
- Room DAO, entity, mapper, repository implementation;
- map object source/cache/store fields that exist only for watchtower definitions;
- map presenters, marker state, bottom sheet, sheet UI state, strings;
- `MapViewModel` watchtower selection, claim, upgrade, and resource amount wiring;
- DI bindings that expose the watchtower repository.

Delete or rewrite watchtower tests:

- domain use case tests;
- generation tests;
- repository tests;
- DAO tests;
- presenter tests;
- map view model tests that assert selected watchtower behavior.

Keep generic tests for resources, fog, map startup, map style, and exploration tracking.

## Epic Tickets

### Ticket 1: Remove Watchtower Feature

Purpose: eliminate old area-control behavior and make the app compile without watchtower concepts.

Sub-tasks:

- delete watchtower domain/data/UI files;
- remove watchtower strings;
- remove watchtower dependencies from `MapViewModel`;
- remove watchtower data from map-object area cache/store/source;
- add Room migration `5 -> 6`;
- delete or update watchtower tests;
- compile Kotlin and run focused database migration tests.

### Ticket 2: Breach Domain And Persistence

Purpose: add the breach node data model, deterministic generation, repository, and persistence without UI.

Sub-tasks:

- create breach node domain models;
- create deterministic breach generation;
- add `breach_node_state` entity and DAO;
- add repository implementation;
- add repository DI binding;
- add generator, repository, DAO, and migration tests.

### Ticket 3: Breach Scan And Completion Use Cases

Purpose: implement the core gameplay rules independent of Compose and MapLibre.

Sub-tasks:

- add scan range and upload balance constants;
- implement nearest uncontrolled breach scan;
- implement discovery on proximity;
- implement upload completion with range validation;
- implement controlled breach reveal tiles;
- add focused JVM tests around scan, discovery, completion, and reveal.

### Ticket 4: Map ViewModel Session Flow

Purpose: connect Breach Protocol use cases to map state and intents.

Sub-tasks:

- add breach protocol UI state models;
- add map intents for pulse, upload, cancellation, and dismissal;
- derive signal strength from current location and locked target;
- transition to in-range state when location is close enough;
- advance upload progress while the player remains in radius;
- complete the breach and refresh fog state;
- add `MapViewModelTest` coverage for each state transition.

### Ticket 5: Breach Map UI Overlays

Purpose: make the core loop playable through Compose map overlays.

Sub-tasks:

- add Pulse button;
- add signal strength panel;
- add terminal upload panel;
- add completion log panel;
- add map screen tests for overlay visibility and click dispatch;
- keep layout responsive and avoid rebuilding the old general HUD.

### Ticket 6: Dynamic Map Object Rendering

Purpose: render breach node markers and support marker taps.

Sub-tasks:

- add `MapObjectKind.BreachNode`;
- add breach node presenter;
- pass `visibleObjects` into map rendering;
- render breach markers through MapLibre or a Compose overlay marker layer;
- dispatch marker taps to map intents;
- add presenter and map interop tests where feasible.

### Ticket 7: Visible Sector Reveal

Purpose: make controlled breach reveal visible on the map.

Sub-tasks:

- replace watchtower reveal observation in `FogOfWarController`;
- reconnect or replace the commented native fog layer path;
- verify controlled breach tiles clear persistent fog;
- add fog controller tests for controlled breach reveal;
- perform manual runtime validation on a device or emulator.

### Ticket 8: Polish And Follow-Up Mechanics

Purpose: add the atmospheric and risk/reward mechanics after the core loop is proven.

Sub-tasks:

- add scan pulse animation;
- add net-vision visual mode;
- add upload failure and lockdown;
- add gyroscope or slider stability mechanic;
- add audio feedback;
- add passive rewards or sector buffs only after the resource economy is defined.

## Testing Strategy

Use focused tests at each layer:

- domain generation and use case JVM tests for deterministic gameplay rules;
- Room DAO and migration instrumentation tests for persistence;
- `MapViewModelTest` for session transitions and effect messages;
- `MapScreenTest` for Compose overlays;
- fog controller tests for persistent reveal;
- runtime validation for MapLibre marker and fog rendering.

Use the narrowest commands for each ticket. At minimum, production-code tickets should run:

```shell
./gradlew :app:compileDebugKotlin -q
```

Database migration or Android test changes should also run:

```shell
./gradlew :app:assembleDebugAndroidTest
```

## Risks

The largest risk is trying to implement the cinematic vision before the map object and fog rendering foundation is
reconnected. The epic should therefore prioritize domain correctness, persistence, and a simple playable overlay loop
before visual polish.

The second risk is deleting watchtowers and replacing them in one large patch. The removal ticket should be completed
and verified first, then breach domain work should start from a clean compile.

The third risk is overloading hero energy. The current model stores energy, but there is no clear scan-spending and
regeneration behavior. Energy cost should stay out of the first playable Breach Protocol version.

## Acceptance Criteria

The epic is complete when:

- no production or test code references watchtowers;
- the database schema stores breach node state instead of watchtower state;
- the player can pulse-scan for a nearby uncontrolled breach node;
- signal strength changes as the player approaches;
- the breach becomes interactable in range;
- upload completion marks the breach controlled;
- controlled breach nodes permanently clear their sector from fog of war;
- focused JVM, migration, Compose, and compile checks pass.
