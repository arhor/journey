# AGENTS.md

This file gives coding agents project-specific instructions for this repository. It applies to the whole repository
unless a more deeply nested `AGENTS.md` overrides it.

## Project Overview

- `journey` is a single-module Android app in `:app`.
- Primary app code is Kotlin under `app/src/main/java/com/github/arhor/journey`.
- Kotlin source files intentionally live in the Android `java` source sets.
- UI is Jetpack Compose with Material 3.
- Dependency injection uses Hilt.
- Persistence uses Room and DataStore.
- Maps use MapLibre. Map style assets live under `app/src/main/assets/map/styles`.

## Repository Layout

- `settings.gradle.kts`: includes only `:app`.
- `build.gradle.kts`: shared Kotlin compiler options and test defaults.
- `gradle/libs.versions.toml`: dependency and plugin versions.
- `app/build.gradle.kts`: Android, Compose, Hilt, Room, and MapLibre configuration.
- `app/src/main/java/com/github/arhor/journey/core`: shared app primitives, UI components, and navigation.
- `app/src/main/java/com/github/arhor/journey/data`: Room, DataStore, repositories, mappers, local generation.
- `app/src/main/java/com/github/arhor/journey/domain`: models, repository contracts, use cases, domain policies.
- `app/src/main/java/com/github/arhor/journey/feature`: screen and feature implementations.
- `app/src/test/java`: JVM unit tests.
- `app/src/androidTest/java`: instrumentation and Compose UI tests.

## Build And Validation Commands

Use the narrowest relevant command for the files changed.

- Compile Kotlin after production-code changes:
  `./gradlew :app:compileDebugKotlin -q`
- Run all JVM unit tests:
  `./gradlew :app:testDebugUnitTest`
- Run one JVM test class:
  `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.package.ClassNameTest"`
- Assemble instrumentation tests after Android test changes:
  `./gradlew :app:assembleDebugAndroidTest`
- Compile Hilt instrumentation-test sources after DI changes affecting `androidTest`:
  `./gradlew :app:hiltJavaCompileDebugAndroidTest`
- Assemble the debug app after native C++ or MapLibre interop changes:
  `./gradlew :app:assembleDebug`
- Run lint when changing Android resources, manifests, or UI:
  `./gradlew :app:lintDebug`

## Coding Conventions

- Prefer small, focused changes that follow the surrounding package structure.
- Keep Kotlin code idiomatic and explicit. Use immutable data classes for UI state and domain values.
- Keep feature UI state, intents, effects, routes, screens, and view models in the relevant `feature/<name>` package.
- Prefer existing project primitives such as `Output`, `DomainError`, `ResourceType`, and `MviViewModel` instead of
  adding parallel result or state abstractions.
- Use constructor injection with Hilt for production collaborators.
- Keep domain logic independent from Android framework types when practical.
- Use `Flow` for observable state and repository streams. Avoid blocking calls in UI and ViewModel code.
- Keep Compose functions stateless where practical. Hoist state to ViewModels or route-level composables.
- Use `@Immutable` or `@Stable` only when the type actually satisfies the contract.
- Avoid adding dependencies unless the existing stack cannot reasonably solve the problem.
- In native C++ code under `app/src/main/cpp`, name constants with `snake_case`; do not use Hungarian-style prefixes
  such as `kConstantName`, and do not use all-caps constant names except for external macros required by APIs.

## Testing Guidance

- Add or update focused JVM tests for domain, repository, presenter, policy, and ViewModel behavior.
- Name tests with backtick-quoted sentences that follow:
  `{function_name} should {expected_behavior} when {given_context}`.
- Structure each test body with `// Given`, `// When`, and `// Then` sections, in that order.
- Use `runTest`, `StandardTestDispatcher`, and the existing coroutine test helpers for coroutine code.
- Use Kotest assertions and MockK consistently with existing tests.
- Add Android or Compose instrumentation tests only when behavior depends on Android framework, Room migrations,
  permissions, or real Compose semantics.
- For Room schema changes, update migrations and migration tests in `app/src/androidTest`.
- For MapLibre interop or native map behavior, prefer tests around adapters, controllers, and policy classes unless a
  full instrumentation test is required.

## Map And Location Notes

- Treat latitude and longitude ordering carefully. Project model types usually use `lat` then `lon`.
- Keep tile, viewport, and fog-of-war calculations deterministic and unit tested.
- Avoid querying or rendering map objects without considering the active viewport/query-window policy.
- Keep map style IDs and asset URIs consistent with `MapStyle` and `app/src/main/assets/map/styles`.

## Documentation And Planning

- Keep docs concise and task-oriented.
- Do not update generated build output or IDE metadata unless the task explicitly requires it.

## Git And Change Hygiene

- Check `git status --short` before and after edits.
- Do not revert user changes unless explicitly asked.
- Keep unrelated refactors out of the patch.
- Mention commands run and any commands that could not be run in the final response.
