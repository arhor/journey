# Repository Guidelines

## Maintenance Note For Coding Agents
This file must stay in sync with the repo.
If you add or remove modules, move source sets, change architecture conventions,
update toolchain requirements, or change the recommended build/test commands,
update `AGENTS.md` in the same change.

## Project Structure & Module Organization
This repository is a multi-module Android app built with Kotlin, Jetpack Compose, Hilt, Room, and DataStore.

Current Gradle modules:

- `:app` - application shell, `MainActivity`, app scaffold, root navigation graph, and app-level Hilt modules.
- `:domain` - pure Kotlin/JVM domain layer with models, repository contracts, use cases, and progression logic.
- `:data` - Android data layer with Room database/DAOs/entities, DataStore-backed repositories, mappers, and seeds.
- `:core:common` - shared non-UI primitives such as `Output`, `DomainError`, and qualifiers.
- `:core:navigation` - shared navigation types such as `BottomNavDestination`.
- `:core:ui` - shared UI architecture support; currently this is primarily `MviViewModel`, not a shared widget library.
- `:feature:hero` - hero screen, route, navigation contract, and view model.
- `:feature:map` - map flow, POI flows, map rendering helpers, location tracking bindings, and related view models.
- `:feature:settings` - settings screen, navigation contract, Health Connect entry points, and view model.

Primary source locations:

- App: `app/src/main/kotlin/com/github/arhor/journey`
- Domain: `domain/src/main/kotlin/com/github/arhor/journey/domain`
- Data: `data/src/main/kotlin/com/github/arhor/journey/data`
- Core common: `core/common/src/main/java/com/github/arhor/journey`
- Core navigation: `core/navigation/src/main/kotlin/com/github/arhor/journey/core/navigation`
- Core UI: `core/ui/src/main/kotlin/com/github/arhor/journey/core/ui`
- Features: `feature/<name>/src/main/kotlin/com/github/arhor/journey/feature/<name>`
- App resources: `app/src/main/res`

Build configuration lives in:

- `settings.gradle.kts`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `data/build.gradle.kts`
- `domain/build.gradle.kts`
- `core/*/build.gradle.kts`
- `feature/*/build.gradle.kts`
- `gradle/libs.versions.toml`
- `.github/workflows/android-ci.yml`

## Architecture & Dependency Rules
Keep the module dependency direction intact:

- `:app -> :data, :domain, :core:*, :feature:*`
- `:feature:* -> :domain, :core:*`
- `:data -> :domain, :core:common`
- `:domain -> :core:common`

Practical rules:

- Keep `:domain` Android-free.
- Put new app wiring and singleton bindings in `app/src/main/kotlin/com/github/arhor/journey/di`.
- Keep feature-specific platform bindings inside the owning feature module when they are not truly app-wide.
- Root navigation is assembled in `app/ui/navigation/AppNavGraph.kt`;
  features own their typed destinations and `*Graph(...)` builders.
- Use typed navigation contracts with `@Serializable` destinations and
  `composable<T>` routes, matching the existing feature modules.
- Treat `app` as the composition root, not as the default place for new business logic.

UI/state-management conventions:

- The main screen pattern is MVI with explicit `Intent`, `UiState`, and `Effect` types.
- For `MviViewModel` screens, implement `buildUiState()` by combining
  internal `MutableStateFlow` state with domain flows.
- Update internal state via `_state.update { ... }` reducers inside intent handlers.
- Emit transient UI events through `effects`; do not smuggle one-off events into persistent UI state.
- Keep domain-to-UI mapping in pure helpers when possible.
- Not every screen has to extend `MviViewModel`;
  simple flows such as `AddPoiViewModel` can stay as plain `ViewModel`
  implementations when that is the better fit.

Shared UI placement:

- Keep feature-local reusable UI in the owning feature module.
- Keep app-shell UI in `app/ui` (`App`, bottom bar, theme, root-level components).
- If a Compose component or UI helper is reused across multiple features,
  prefer moving it into `:core:ui` instead of `:app`.

Data/domain conventions:

- Repository interfaces live in `:domain`; implementations live in `:data`.
- Room entities, DAOs, and mappers stay in `:data`.
- Use the existing typed `Output<T, E : DomainError>` pattern for
  recoverable domain/data flows that already model success/failure explicitly.

## Build, Test, and Development Commands
Use the Gradle wrapper from repo root. JDK 17 is required; CI uses Temurin 17.

- `./gradlew assembleDebug` builds a debug APK.
- `./gradlew assembleRelease` builds a release APK.
- `./gradlew lintDebug` runs Android lint across Android modules.
- `./gradlew test testDebugUnitTest` runs JVM/unit tests across modules.
- `./gradlew connectedDebugAndroidTest` runs instrumentation/Compose tests on a connected device or emulator.
- `./gradlew lintDebug test testDebugUnitTest assembleRelease --stacktrace` matches the main CI verification job.

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

Follow the existing naming and structure patterns in each feature:

- `FeatureIntent`, `FeatureUiState`, `FeatureEffect`, `FeatureViewModel`
- `FeatureRoute`, `FeatureScreen`, `FeatureNavigationContract`
- `Observe...UseCase`, `Set...UseCase`, `Add...UseCase`, etc.

Do not reintroduce the old `app/ui/views/<feature>` layout.
New features belong in their own `feature/<feature>` module unless the
architecture is intentionally being changed.

## Testing Guidelines
Put tests in the module that owns the code:

- JVM tests: `<module>/src/test/kotlin`
- Instrumented/UI tests: currently `app/src/androidTest/kotlin`

Current test stack includes:

- JUnit4
- Kotest assertions
- MockK
- `kotlinx-coroutines-test`
- AndroidX test runner and JUnit extensions
- Espresso
- Compose UI test APIs
- Hilt Android testing

Test conventions used in the repo:

- Prefer backtick test names in the form `{function/method/action} should {expected behavior} when {given context}`.
- Split tests visually into `// Given`, `// When`, and `// Then`.
- Keep fast logic tests in JVM source sets.
- If you add instrumentation tests in feature modules later, keep root `connectedDebugAndroidTest` execution healthy.

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
