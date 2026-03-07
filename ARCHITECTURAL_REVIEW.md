# Architectural Review and Refactoring Plan

## Executive summary
The repository has a solid baseline: a clear single-module structure, explicit domain models/use cases, and mostly consistent MVI-style screen contracts. The strongest area is the separation between domain/use case and data persistence logic. The weakest area is **boundary discipline around Health Connect permission orchestration**, where presentation directly depends on a data gateway and duplicates permission semantics already represented in the domain layer.

The refactoring pass focuses on correcting dependency direction and tightening MVI intent/effect semantics without changing user-visible behavior.

## Strengths
- Consistent feature packaging under `ui/views/<feature>` and reusable UI under `ui/components`.
- ViewModels follow a common `MviViewModel` base and expose immutable UI contracts (`UiState`, `Intent`, `Effect`).
- Domain logic for progression/reward is encapsulated in use cases + progression engine, with transactional boundaries.
- Data mappers and repository implementations are generally cohesive and narrow.
- Existing unit/instrumentation tests already cover key architectural seams (VM flows, map style resolution, health sync logic).

## Critical issues
1. **Presentation layer depends on a data-layer gateway (`HealthConnectPermissionGateway`)**.
   - `SettingsViewModel` injects `HealthConnectPermissionGateway` directly instead of a domain abstraction.
   - This violates dependency direction and introduces Android/provider permission details into presentation orchestration.

2. **Domain abstraction underused / partially bypassed**.
   - `HealthPermissionRepository` exists, and `SyncHealthDataUseCase` already uses it, but presentation bypasses it for missing permission reads.
   - This creates split authority over permission semantics and increases coupling.

## Moderate issues
1. **Intent contract includes unused payload**.
   - `SettingsIntent.HandleHealthConnectPermissionResult` carries `grantedPermissions`, but VM ignores the payload and re-reads current permission state.
   - This is misleading and weakens intent clarity.

2. **SettingsViewModel is very large and multi-responsibility**.
   - It orchestrates settings updates, HC availability/permission lifecycle, sync progression, UI summary aggregation.
   - Works now, but future complexity risk is high.

3. **UI-layer utility (`MapStyleRepository`) includes IO + parsing in presentation package**.
   - Not immediately broken, but boundary intent is less clear (it’s infrastructure-ish and not pure UI).

## Minor issues
- Inconsistent error message constants across view models (many inline literals).
- Some duplication in health sync lookback window resolution (`SettingsViewModel` and worker).
- `MviViewModel` contains logger acquisition that is currently not leveraged for diagnostics.

## Refactoring plan
1. **Fix boundary leak in settings flow**
   - Extend `HealthPermissionRepository` with missing-permissions query.
   - Implement in `HealthConnectPermissionRepositoryImpl` via gateway.
   - Replace direct gateway dependency in `SettingsViewModel` with domain repository.

2. **Clean intent semantics**
   - Convert `HandleHealthConnectPermissionResult` to a payload-less object intent to reflect actual handling.
   - Update route + tests.

3. **Stabilize tests**
   - Update affected unit tests to mock domain abstraction instead of gateway.
   - Keep behavior assertions identical.

## Open questions / assumptions
- Assumption: Permission result handling should remain authoritative by re-querying current permission state, not trusting launcher payload.
- Assumption: Existing Health Connect permission set remains gateway-defined; domain abstraction just forwards currently.
- Open question: whether to extract Health Connect orchestration into a dedicated coordinator/use case in a follow-up.

## Second-pass findings
After re-checking the repository with fresh angles:
- No additional critical boundary violations beyond the Health Connect permission leak were found.
- Main latent risk remains `SettingsViewModel` size/cohesion; not fully addressed in this pass to keep refactor incremental and safe.
- Map feature and home feature maintain good UDF discipline; no direct composable business-logic violations were identified beyond presentational formatting logic.
