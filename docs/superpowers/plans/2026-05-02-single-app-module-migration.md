# Single App Module Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Flatten the Android project into a single Gradle module, `:app`, while preserving package names and behavior.

**Architecture:** Keep the current domain, data, core, feature, and fog-of-war boundaries as package-level conventions inside `:app`. Remove Gradle project boundaries and make `app/build.gradle.kts` own all dependencies, KSP processors, Android components, resources, assets, and tests.

**Tech Stack:** Android Gradle Plugin 9.2.0, Kotlin 2.3.21, Jetpack Compose, Hilt, Room, DataStore, MapLibre, Godot Android, JUnit4, Kotest, MockK, AndroidX test.

---

### Task 1: Move Source Sets Into App

**Files:**
- Move production Kotlin/Java from `core`, `data`, and `feature` modules into `app/src/main/...`.
- Move JVM tests into `app/src/test/kotlin`.
- Move instrumentation tests into `app/src/androidTest/kotlin`.

- [x] Move `core/common/src/main/java` into `app/src/main/java`.
- [x] Move every former module `src/main/kotlin` tree into `app/src/main/kotlin`.
- [x] Move every former module `src/test/kotlin` tree into `app/src/test/kotlin`.
- [x] Move `core/testing/src/main/kotlin` into `app/src/test/kotlin` because the helper is test-only after flattening.
- [x] Move every former module `src/androidTest/kotlin` tree into `app/src/androidTest/kotlin`.
- [x] Confirm there are no duplicate destination source paths.

### Task 2: Merge Resources, Assets, And Manifests

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Move: feature map drawable resources into `app/src/main/res/drawable`
- Move: Godot project from `feature/mini-game/jrpg` into `app/src/main/godot`

- [x] Append unique string resources from former module `values/strings.xml` files into the app strings file.
- [x] Move map drawable PNG resources into app drawables.
- [x] Move the Godot project directory to `app/src/main/godot`.
- [x] Add `POST_NOTIFICATIONS` to the app manifest.
- [x] Add `ExplorationTrackingForegroundService` to the app manifest.
- [x] Add `MiniGameActivity` to the app manifest using the same config changes and exported flag.

### Task 3: Collapse Gradle Modules

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`

- [x] Change `settings.gradle.kts` so it includes only `:app`.
- [x] Remove all `project(...)` dependencies from `app/build.gradle.kts`.
- [x] Add direct app dependencies formerly owned by removed modules.
- [x] Add Room compiler KSP support to `:app`.
- [x] Add Godot export task wiring to `:app`, using `app/src/main/godot` as input and generated app assets as output.
- [x] Keep `androidResources.noCompress.add("pck")` in `:app`.

### Task 4: Remove Old Module Directories

**Files:**
- Remove old `core`, `data`, and `feature` module source/build directories after their contents are migrated.

- [x] Delete old module directories that no longer contain source, resources, tests, or owned assets.
- [x] Confirm no `project(":...")` references remain.
- [x] Confirm no old module source paths remain in active Gradle configuration.

### Task 5: Update Repository Guidance

**Files:**
- Modify: `AGENTS.md`

- [x] Replace the multi-module module list with the single `:app` module layout.
- [x] Keep package-level architecture guidance for domain, data, core, feature, and fog-of-war packages.
- [x] Update build/test commands to single-module equivalents.
- [x] Remove guidance that instructs new features to create Gradle feature modules.

### Task 6: Verify And Fix Migration Fallout

**Commands:**
- `./gradlew :app:compileDebugKotlin -q`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:assembleDebugAndroidTest`

- [x] Run `:app:compileDebugKotlin` and fix compile errors.
- [x] Run `:app:testDebugUnitTest` and fix test/runtime errors.
- [x] Run `:app:assembleDebugAndroidTest` and fix instrumentation build errors.
- [x] Inspect `git status --short` and `git diff --stat`.
