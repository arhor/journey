# Repository Guidelines

## Project Structure & Module Organization
This repository is a single-module Android app (`:app`) built with Kotlin + Jetpack Compose.

- App code: `app/src/main/kotlin/com/github/arhor/journey`
- Resources/assets: `app/src/main/res`
- Unit tests (JVM): `app/src/test/kotlin`
- Instrumented/UI tests: `app/src/androidTest/kotlin`
- Build config: `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`
- CI workflow: `.github/workflows/android-ci.yml`

Organize features under `ui/views/<feature>` (for example `home`, `settings`) and keep shared UI in `ui/components`.

## Build, Test, and Development Commands
Use the Gradle wrapper from repo root:

- `./gradlew assembleDebug` builds a debug APK.
- `./gradlew assembleRelease` builds a release APK.
- `./gradlew lintDebug` runs Android lint for debug.
- `./gradlew test testDebugUnitTest` runs JVM unit tests.
- `./gradlew connectedDebugAndroidTest` runs instrumentation/Compose tests on a connected emulator/device.
- `./gradlew lintDebug test testDebugUnitTest assembleRelease --stacktrace` matches CI’s main verification job.

Use JDK 17 (CI uses Temurin 17).

## Coding Style & Naming Conventions
Style is enforced via `.editorconfig` and Gradle settings:

- Indentation: 4 spaces; max line length: 120; UTF-8 + LF.
- Kotlin style: `official` (`gradle.properties`).
- Package names: lowercase (`com.github.arhor.journey...`).
- Type names: PascalCase (`HomeViewModel`, `AppNavGraph`).
- Keep feature contracts explicit with `Intent`, `UiState`, and `Effect` types.
- For `MviViewModel` screens, prefer unidirectional state composition:
  implement `buildUiState()` by combining internal feature state (`MutableStateFlow`) with domain flows,
  update internal state via `_state.update { ... }` in intent handlers,
  and keep domain-to-UiState mapping in pure helper functions.

## Testing Guidelines
- Put fast logic tests in `app/src/test`; Android/Compose behavior tests in `app/src/androidTest`.
- Frameworks in use: JUnit4, AndroidX test runner, Compose UI test APIs; MockK and Kotest assertions are available.
- Name test files with `*Test.kt`.
- Use backtick test names in the form: `{function/method/action} should {expected behavior} when {given context}`.
- Split each test visually into 3 sections with comments: `// Given`, `// When`, `// Then`.
- If strict phrasing is not possible (for example framework constraints), use the closest readable equivalent while preserving intent.

## Commit & Pull Request Guidelines
Recent history favors short, imperative, sentence-style commits (for example: `Refactor ViewModels to use a new base MviViewModel class.`).

For PRs:
- Describe what changed and why.
- Link the issue/task when applicable.
- List commands you ran locally.
- Include screenshots or recordings for UI changes.
- Ensure CI checks pass before merge.

## Security & Configuration Tips
Do not commit local secrets or machine-specific files (`local.properties`, keystores, `google-services.json`, build outputs). Keep environment-specific values out of source control.
