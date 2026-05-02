# Repository Guidelines

## Maintenance Note For Coding Agents
This file must stay in sync with the repo.
If you add or remove modules, move source sets, change architecture conventions,
update toolchain requirements, or change recommended build/test commands,
update `AGENTS.md` in the same change.

## Accepted-Plan Handoff
When the user affirms the immediately preceding `<proposed_plan>` with a short acceptance such as `yes`,
`implement it`, or `proceed`, treat that as authorization to execute the accepted plan rather than as a new
one-line request.

For accepted-plan execution:

- Use the implementation handoff contract fields `plan_status`, `recommended_skills`, `review_policy`, and
  `acceptance_behavior` as the source of truth for whether execution should start and which skills should activate.
- Reconstruct the implementation task from the accepted plan plus the original user request.
- Re-infer applicable implementation skills from that reconstructed task instead of relying on the literal
  acceptance message.
- Do not model this as carrying skills across turns. Treat it as rebuilding the current implementation context.
- Auto-apply `implement-compose-feature` when the accepted plan is implementation-ready and clearly describes Android
  Jetpack Compose feature work.
- Auto-apply `swarm-code-review` for non-trivial code changes unless the user explicitly asked to skip review.
- If the plan handoff marks the plan as non-implementation or investigation-only, do not start coding from acceptance
  alone.

## Project Structure & Module Organization
This repository is a single-module Android app built with Kotlin, Jetpack Compose, Hilt, Room, and DataStore.

Gradle modules:

- `:app` - the only Gradle module. It contains the application shell, domain layer, data layer, shared core code,
  feature packages, fog-of-war implementation, Godot mini-game launch surface, resources, and tests.

Primary source locations:

- App shell: `app/src/main/kotlin/com/github/arhor/journey`
- Domain: `app/src/main/kotlin/com/github/arhor/journey/domain`
- Data: `app/src/main/kotlin/com/github/arhor/journey/data`
- Core common: `app/src/main/java/com/github/arhor/journey`
- Core navigation: `app/src/main/kotlin/com/github/arhor/journey/core/navigation`
- Core UI: `app/src/main/kotlin/com/github/arhor/journey/core/ui`
- Features: `app/src/main/kotlin/com/github/arhor/journey/feature/<name>`
- Map fog of war: `app/src/main/kotlin/com/github/arhor/journey/feature/map/fow`
- Godot mini-game project: `app/src/main/godot`
- Generated Godot mini-game bundle: `app/build/generated/godot/assets/minigame.pck`
- App resources: `app/src/main/res`

Build configuration lives in:

- `settings.gradle.kts`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `.github/workflows/android-ci.yml`

## Architecture & Dependency Rules
Keep the former module boundaries as package-level architecture boundaries:

- App shell code may depend on data, domain, core, and feature packages.
- Core UI may depend on core common primitives.
- Feature packages may depend on domain and core packages.
- Map package may depend on the fog-of-war package.
- Data package may depend on domain and core common packages.
- Domain package may depend on core common primitives.

Practical rules:

- Keep `com.github.arhor.journey.domain` Android-free.
- Treat `com.github.arhor.journey` app-shell packages as the composition root, not as the default place for new business logic.
- Put app-wide wiring and singleton bindings in `app/src/main/kotlin/com/github/arhor/journey/di`.
- Keep feature-specific platform bindings inside the owning feature package when they are not truly app-wide.
- `:app` owns the Godot mini-game project under `app/src/main/godot`, exports `minigame.pck` through the
  `exportGodotPack` Gradle task when `godot.bin` or `GODOT_BIN` is configured, and owns the Android `GodotActivity`
  launch surface.
- Root navigation is assembled in `app/ui/navigation/AppNavGraph.kt`; features own typed destinations and `*Graph(...)` builders.
- Use typed navigation contracts with `@Serializable` destinations and `composable<T>` routes, matching existing feature modules.
- `com.github.arhor.journey.feature.map` may start, stop, or observe exploration tracking sessions, but it must not own
  continuous location collection or the tile-reveal pipeline.
- Keep fog-of-war implementation details in `com.github.arhor.journey.feature.map.fow`; map code should consume its
  public state/controller API instead of rebuilding fog logic locally.

## UI & State Management Conventions
Main screen pattern:

- Prefer MVI with explicit `Intent`, `UiState`, and `Effect` types.
- For `MviViewModel` screens, derive `buildUiState()` from internal `MutableStateFlow` state plus domain flows.
- Update internal state with `_state.update { ... }` reducers inside intent handlers.
- Emit one-off UI events through `effects`, not through persistent UI state.
- Keep domain-to-UI mapping in pure helpers when possible.
- Not every screen has to extend `MviViewModel`; simple flows can stay plain `ViewModel` when that is the better fit.

Shared UI placement:

- Keep feature-local reusable UI in the owning feature package.
- Keep app-shell UI in `app/ui`.
- If a Compose component or UI helper is reused across multiple features, prefer moving it into
  `com.github.arhor.journey.core.ui` instead of app-shell packages.

## Data & Domain Conventions

- Repository interfaces live in `com.github.arhor.journey.domain`; implementations live in
  `com.github.arhor.journey.data`.
- Room entities, DAOs, and mappers stay in `com.github.arhor.journey.data`.
- Use the existing typed `Output<T, E : DomainError>` pattern as a strict use-case boundary contract.
- Every use case class must return `Output` for one-shot operations or `Flow`/`StateFlow` of `Output` for observable streams.
- Do not expose raw values, nullable results, or bare `Unit` from use case APIs, even when the current implementation looks infallible.
- Convert repository exceptions and missing-data cases into typed `Output.Failure(...)` values at the use case boundary.
- UI and runtime callers must unwrap and handle both `Output.Success` and `Output.Failure` explicitly; do not ignore failures or rely on thrown exceptions for normal control flow.

## Build, Test, and Development Commands
Use the Gradle wrapper from repo root. JDK 17 is required; CI uses Temurin 17.

- `./gradlew assembleDebug` builds a debug APK.
- `./gradlew assembleRelease` builds a release APK.
- `./gradlew lintDebug` runs Android lint for the app module.
- `./gradlew testDebugUnitTest` runs JVM/unit tests for the app module.
- `./gradlew connectedDebugAndroidTest` runs instrumentation/Compose tests on a connected device or emulator.
- `./gradlew lintDebug testDebugUnitTest assembleRelease --stacktrace` matches the main CI verification job.

The repo also includes `run/setup.sh` for bootstrapping Android SDK command-line tooling in a fresh environment.

## Coding Style & Naming Conventions
Style is enforced via `.editorconfig` and Gradle settings:

- Indentation: 4 spaces
- Max line length: 120
- UTF-8 + LF
- Kotlin style: `official`
- Package names: lowercase
- Type names: PascalCase
- Test files: `*Test.kt`

Follow existing naming and structure patterns in each feature:

- `FeatureIntent`, `FeatureUiState`, `FeatureEffect`, `FeatureViewModel`
- `FeatureRoute`, `FeatureScreen`, `FeatureNavigationContract`
- `Observe...UseCase`, `Set...UseCase`, `Add...UseCase`, etc.

Do not reintroduce the old `app/ui/views/<feature>` layout.
New features belong in `app/src/main/kotlin/com/github/arhor/journey/feature/<feature>`.

## Testing Guidelines
Put tests in the module that owns the code:

- JVM tests: `app/src/test/kotlin`
- Instrumented/UI tests: `app/src/androidTest/kotlin`

Current test stack includes:

- JUnit4
- Kotest assertions
- MockK
- `kotlinx-coroutines-test`
- AndroidX test runner and JUnit extensions
- Espresso
- Compose UI test APIs
- Hilt Android testing

Repo test conventions:

- Prefer backtick test names in the form `{function/method/action} should {expected behavior} when {given context}`.
- Split tests visually into `// Given`, `// When`, and `// Then`.
- Keep fast logic tests in JVM source sets.
- Keep root `connectedDebugAndroidTest` execution healthy when adding instrumentation tests.

## Commit & Pull Request Guidelines
Recent history favors short, imperative, sentence-style commits.

For PRs:

- Describe what changed and why.
- Link the issue or task when applicable.
- List the commands you ran locally.
- Include screenshots or recordings for UI changes.
- Ensure CI checks pass before merge.

## Security & Configuration Tips
Do not commit secrets, local machine configuration, or generated outputs such as:

- `local.properties`
- keystores
- `google-services.json`
- build outputs
- temporary SDK/bootstrap artifacts

## Implementation Swarm Orchestration

For non-trivial implementation work that starts from an accepted repository-specific plan, prefer
`orchestrate-implementation-swarm` as the top-level execution skill when the change is likely to:

- touch multiple modules, layers, or responsibilities,
- require more than one focused implementation thread,
- benefit from separating implementation, integration, and verification concerns, or
- produce enough tool or terminal output that the main thread context would become noisy.

When this orchestration path is used:

- The orchestrator owns strategy, decomposition, work assignment, and final synthesis.
- Prefer partitioning work by ownership and file boundaries rather than by arbitrary numbered steps.
- Cap parallel implementation agents at 4 unless the task is unusually broad and file ownership is still clean.
- Implementation subagents should avoid running Gradle tasks directly.
- A single designated verifier should own Gradle-based validation. Use `validate-android-change` for that stage.
- Raw terminal output should be summarized with `summarize-execution-artifacts` before it reaches the orchestrator.
- If multiple focused implementation outputs must be combined, use `integrate-subagent-patches` before final validation.
- After validation, run `swarm-code-review` by default for non-trivial code changes unless the user explicitly asked to skip review.

Accepted-plan execution should continue to reconstruct the implementation context from:
- the accepted plan,
- the original user request, and
- the implementation handoff contract.

When the accepted plan is implementation-ready and the expected patch is non-trivial, the orchestrator may activate
repo-specific implementation skills for focused packets, such as:
- `implement-compose-feature`
- `add-android-tests`
- `review-android-architecture`
- `review-compose-ui`
- `audit-compose-performance`

The orchestrator should keep the main thread concise and strategic. It should not accumulate raw command logs, repeated
compile output, or long step-by-step operational noise unless those details are directly needed to make a decision.
