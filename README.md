# Journey

Journey is a single-module Android app built with Kotlin and Jetpack Compose. It combines a MapLibre map with
location-based exploration, fog-of-war tiles, breach nodes, and background exploration tracking. Map styles are bundled
in `app/src/main/assets/map/styles`.

## Requirements

- Android Studio with an Android SDK installed
- JDK 17 (the repository's Gradle launcher baseline)
- Android SDK Platform 37
- NDK `29.0.14206865` and CMake `4.1.2` for the native map layers
- An Android device or emulator using the app's minimum API level, 35

The Gradle wrapper provisions the compatible daemon JVM when needed. Android Studio creates `local.properties`
automatically; it is intentionally local-only and must not be committed.

## Build and test

From the repository root:

```shell
./gradlew :app:compileDebugKotlin -q
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:assembleDebugAndroidTest
```

`assembleDebugAndroidTest` compiles the Room migration, Hilt, and Compose instrumentation-test sources. Running those
tests additionally requires a connected device or emulator:

```shell
./gradlew :app:connectedDebugAndroidTest
```

For a focused JVM test, pass its fully qualified class name with Gradle's `--tests` option.

## Project layout

- `app/src/main/java/com/github/arhor/journey/core` contains shared primitives and UI components.
- `app/src/main/java/com/github/arhor/journey/data` contains Room, DataStore, repositories, and mappers.
- `app/src/main/java/com/github/arhor/journey/domain` contains models, policies, repository contracts, and use cases.
- `app/src/main/java/com/github/arhor/journey/feature` contains feature screens, ViewModels, map interop, and tracking.
- `app/src/main/cpp` contains the native MapLibre rendering layers.
- `app/src/test` and `app/src/androidTest` contain JVM and Android tests respectively.

Contributor and agent conventions are documented in [AGENTS.md](AGENTS.md).
