# Single App Module Migration Design

## Goal

Move the project from a multi-module Android architecture to a single Gradle module, `:app`, while preserving current package names and runtime behavior.

## Final Repository Shape

The root Gradle build will include only `:app`. The current modules under `core`, `data`, and `feature` will no longer be Gradle modules. Their source sets will be physically moved into `app/src/...` instead of being referenced from their old directories.

The final source layout will be:

- `app/src/main/kotlin` for all Kotlin production sources from `:app`, `:data`, `:core:*`, and `:feature:*`.
- `app/src/main/java` for existing Java-root sources from `core/common/src/main/java`.
- `app/src/test/kotlin` for all JVM tests from former modules.
- `app/src/androidTest/kotlin` for all instrumentation and Compose tests from former modules.
- `app/src/main/res` for app, core UI, and feature resources.
- `app/src/main/assets` for current app assets and Godot mini-game assets.

Package names will not be rewritten. For example, `com.github.arhor.journey.domain`, `com.github.arhor.journey.data`, and `com.github.arhor.journey.feature.map` remain valid package namespaces inside the app module.

## Gradle Design

`settings.gradle.kts` will keep the existing plugin management and dependency resolution setup, then include only `:app`.

`app/build.gradle.kts` will absorb the dependency surface from the removed modules:

- Kotlin serialization, Compose, KSP, Hilt, and Android application plugins remain enabled on `:app`.
- Room KSP compiler support moves into `:app`.
- Dependencies formerly declared by `core`, `data`, and `feature` modules become direct `implementation`, `debugImplementation`, `testImplementation`, `androidTestImplementation`, or `kspAndroidTest` dependencies of `:app`.
- Project dependencies such as `implementation(project(":data"))` and `implementation(project(":feature:map"))` are removed.
- Godot mini-game export wiring from `feature/mini-game/build.gradle.kts` moves into `app/build.gradle.kts`.
- `androidResources.noCompress.add("pck")` remains configured for Godot pack files.

The app module will continue to use Java 17, `compileSdk` 37, `minSdk` 35, `targetSdk` 37, Compose, and the current app namespace `com.github.arhor.journey`.

## Manifest And Resource Merge

The app manifest will explicitly own Android components that were previously contributed by library manifests:

- `ExplorationTrackingForegroundService` from the former exploration feature.
- `MiniGameActivity` from the former mini-game feature.

Required permissions from feature manifests will be preserved in `app/src/main/AndroidManifest.xml`, including `POST_NOTIFICATIONS`.

Resources from library modules will move into `app/src/main/res`. Duplicate `values/strings.xml` files will be merged into the app strings file. Existing resource names will be preserved unless a real duplicate conflict appears during the migration; any such conflict will be resolved with the smallest rename that keeps call sites clear.

## Tests

Former module JVM tests will move to `app/src/test/kotlin`. Former module instrumentation tests will move to `app/src/androidTest/kotlin`.

The `core/testing` helper source set will move into `app/src/test/kotlin` because it is only needed by app-local JVM tests after the flattening. Test imports and package names remain unchanged.

Verification will target the single module:

- `./gradlew :app:compileDebugKotlin`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:assembleDebugAndroidTest`

If the migration changes task availability, the implementation will use the closest current single-module Gradle tasks and update `AGENTS.md` accordingly.

## Cleanup

After sources, tests, resources, assets, manifest declarations, and Gradle configuration are migrated, the old module build files and empty module directories will be removed:

- `core/common`
- `core/domain`
- `core/navigation`
- `core/testing`
- `core/ui`
- `data`
- `feature/exploration`
- `feature/hero`
- `feature/map`
- `feature/map/fog-of-war`
- `feature/mini-game`
- `feature/settings`

`AGENTS.md` will be updated in the same change to describe the single-module structure, revised architecture guidance, and updated build/test commands.

## Non-Goals

This migration will not rewrite application architecture into a new package layout. Domain, data, UI, feature, and fog-of-war boundaries remain as package-level conventions inside `:app`.

This migration will not intentionally change app behavior, navigation, database schema, resource values, map behavior, exploration tracking, or mini-game runtime behavior.

This migration will not introduce new abstraction layers to replace Gradle module boundaries.
