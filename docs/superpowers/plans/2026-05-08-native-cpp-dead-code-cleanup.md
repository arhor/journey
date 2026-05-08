# Native Cpp Dead Code Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove unused or dead code under `app/src/main/cpp` while preserving the active MapLibre native tiger layer.

**Architecture:** Kotlin still owns MapLibre `CustomLayer` registration through `NativeModelLayer`, JNI still creates and destroys a `ModelLayer`, and MapLibre still invokes `initialize`, `render`, `contextLost`, and `deinitialize` through the native custom-layer ABI. Cleanup is limited to helpers no longer reachable after the CPU-side clip-space projection migration and a no-op texture coordinate helper.

**Tech Stack:** Kotlin, JNI, CMake, Android NDK, MapLibre custom layer ABI, OpenGL ES 3, cgltf, JUnit4, Kotest.

---

## Findings

Keep these files and APIs because they are reachable:

- `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayer.kt` loads `custom-map-layers` and calls the JNI methods.
- `app/src/main/cpp/lib/jni/custom_map_layers_jni.cpp` exports `createContextNative` and `destroyContextNative`.
- `app/src/main/cpp/include/custom_map_layers/maplibre/custom_layer_host.hpp` mirrors MapLibre Native's custom layer ABI. The virtual methods look unused to text search because MapLibre calls them indirectly through the host pointer.
- `ModelLayer`, `AssetReader`, `ImageDecoder`, `GltfModelLoader`, `LoadedModel`, `cgltf_impl`, `GlError`, `GlesProgram`, `GlTexture`, and `VertexBuffer` are on the active native rendering path.

Cleanup targets:

- `WebMercator::projectToNdc`, `WebMercator::projectMetersOffsetToNdc`, `ScreenPoint`, `degreesToRadiansIfNeeded`, and `kTileSize` are obsolete after `ModelLayer` moved to MapLibre `projectionMatrix` and CPU-side clip projection.
- `gltf/TextureCoordinate.hpp` only exposes `rendererTextureV`, which currently returns its input unchanged. Its only production use can be replaced with `texcoord[1]`.
- `app/src/test/cpp/gltf/TextureCoordinateTest.cpp` is a standalone C++ identity test and is not wired into Gradle or CMake.

## File Structure

- Modify: `app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayerTest.kt` - add cleanup regression tests that fail before deleting obsolete native helpers.
- Modify: `app/src/main/cpp/lib/geo/WebMercator.hpp` - keep only active Mercator conversion declarations and `LocalMeters`.
- Modify: `app/src/main/cpp/lib/geo/WebMercator.cpp` - keep only active Mercator conversion implementations.
- Modify: `app/src/main/cpp/lib/gltf/GltfModelLoader.cpp` - remove the no-op texture coordinate helper include and call.
- Delete: `app/src/main/cpp/lib/gltf/TextureCoordinate.hpp`.
- Delete: `app/src/test/cpp/gltf/TextureCoordinateTest.cpp`.

---

### Task 1: Guard Obsolete Native Helpers With Tests

**Files:**
- Modify: `app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayerTest.kt`

- [ ] **Step 1: Add failing cleanup regression tests**

In `NativeModelLayerTest`, add these tests before the closing brace:

```kotlin
    @Test
    fun `native geo helpers should not keep obsolete manual NDC projection API`() {
        // Given
        val header = File("src/main/cpp/lib/geo/WebMercator.hpp").readText()
        val source = File("src/main/cpp/lib/geo/WebMercator.cpp").readText()

        // When
        val combined = header + "\n" + source

        // Then
        combined shouldNotContain "ScreenPoint"
        combined shouldNotContain "projectToNdc"
        combined shouldNotContain "projectMetersOffsetToNdc"
        combined shouldNotContain "degreesToRadiansIfNeeded"
        combined shouldNotContain "kTileSize"
    }

    @Test
    fun `native gltf loader should not depend on no-op texture coordinate helper`() {
        // Given
        val helper = File("src/main/cpp/lib/gltf/TextureCoordinate.hpp")
        val loader = File("src/main/cpp/lib/gltf/GltfModelLoader.cpp").readText()

        // When
        val helperExists = helper.exists()

        // Then
        helperExists shouldBe false
        loader shouldNotContain "TextureCoordinate.hpp"
        loader shouldNotContain "rendererTextureV"
    }
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
```

Expected: FAIL because `WebMercator` still declares/implements the obsolete NDC projection helpers and `TextureCoordinate.hpp` still exists.

- [ ] **Step 3: Commit the failing tests**

```bash
git add app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayerTest.kt
git commit -m "test: cover native dead code cleanup"
```

---

### Task 2: Remove Obsolete WebMercator Projection API

**Files:**
- Modify: `app/src/main/cpp/lib/geo/WebMercator.hpp`
- Modify: `app/src/main/cpp/lib/geo/WebMercator.cpp`

- [ ] **Step 1: Replace `WebMercator.hpp` with active declarations only**

Use this complete file content:

```cpp
#pragma once

namespace custom_map_layers::geo {

struct LocalMeters {
    double east;
    double north;
    double up;
};

double longitudeToMercatorX(double longitude);
double latitudeToMercatorY(double latitude);
double metersToMercatorUnits(double meters, double latitude);

}  // namespace custom_map_layers::geo
```

- [ ] **Step 2: Replace `WebMercator.cpp` with active implementations only**

Use this complete file content:

```cpp
#include "WebMercator.hpp"

#include <cmath>

namespace custom_map_layers::geo {
namespace {

constexpr double kEarthCircumferenceMeters = 40075016.68557849;
constexpr double kPi = 3.14159265358979323846264338327950288;
constexpr double kDegreesToRadians = kPi / 180.0;

}  // namespace

double longitudeToMercatorX(double longitude) {
    return (longitude + 180.0) / 360.0;
}

double latitudeToMercatorY(double latitude) {
    const double radians = latitude * kDegreesToRadians;
    return (1.0 - std::log(std::tan(radians) + (1.0 / std::cos(radians))) / kPi) / 2.0;
}

double metersToMercatorUnits(double meters, double latitude) {
    const double radians = latitude * kDegreesToRadians;
    return meters / (kEarthCircumferenceMeters * std::cos(radians));
}

}  // namespace custom_map_layers::geo
```

- [ ] **Step 3: Run the focused JVM test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
```

Expected: still FAIL because `TextureCoordinate.hpp` still exists, but the new WebMercator cleanup test assertions should now pass.

- [ ] **Step 4: Compile the native target**

Run:

```bash
./gradlew :app:externalNativeBuildDebug
```

Expected: PASS. `ModelLayer.cpp` should still compile because it uses `LocalMeters`, `longitudeToMercatorX`, `latitudeToMercatorY`, and `metersToMercatorUnits`.

- [ ] **Step 5: Commit WebMercator cleanup**

```bash
git add app/src/main/cpp/lib/geo/WebMercator.hpp app/src/main/cpp/lib/geo/WebMercator.cpp
git commit -m "refactor: remove obsolete native NDC projection helpers"
```

---

### Task 3: Remove No-Op Texture Coordinate Helper

**Files:**
- Modify: `app/src/main/cpp/lib/gltf/GltfModelLoader.cpp`
- Delete: `app/src/main/cpp/lib/gltf/TextureCoordinate.hpp`
- Delete: `app/src/test/cpp/gltf/TextureCoordinateTest.cpp`

- [ ] **Step 1: Remove the helper include**

In `GltfModelLoader.cpp`, delete:

```cpp
#include "gltf/TextureCoordinate.hpp"
```

- [ ] **Step 2: Inline the active texture coordinate behavior**

In `appendPrimitive`, replace:

```cpp
.v = custom_map_layers::gltf::rendererTextureV(texcoord[1]),
```

with:

```cpp
.v = texcoord[1],
```

- [ ] **Step 3: Delete the dead helper and unwired standalone test**

Delete:

```text
app/src/main/cpp/lib/gltf/TextureCoordinate.hpp
app/src/test/cpp/gltf/TextureCoordinateTest.cpp
```

- [ ] **Step 4: Verify no references remain**

Run:

```bash
rg -n "TextureCoordinate|rendererTextureV" app/src/main/cpp app/src/test app/src/androidTest
```

Expected: no matches.

- [ ] **Step 5: Run the focused JVM test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
```

Expected: PASS.

- [ ] **Step 6: Compile the native target**

Run:

```bash
./gradlew :app:externalNativeBuildDebug
```

Expected: PASS.

- [ ] **Step 7: Commit texture helper cleanup**

```bash
git add app/src/main/cpp/lib/gltf/GltfModelLoader.cpp
git add -u app/src/main/cpp/lib/gltf/TextureCoordinate.hpp app/src/test/cpp/gltf/TextureCoordinateTest.cpp
git commit -m "refactor: remove no-op native texture coordinate helper"
```

---

### Task 4: Final Native Dead Code Sweep

**Files:**
- Inspect: `app/src/main/cpp`
- Inspect: `app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayerTest.kt`

- [ ] **Step 1: Verify old helper names are gone**

Run:

```bash
rg -n "projectToNdc|projectMetersOffsetToNdc|ScreenPoint|degreesToRadiansIfNeeded|kTileSize|TextureCoordinate|rendererTextureV" app/src/main/cpp app/src/test app/src/androidTest
```

Expected: no matches.

- [ ] **Step 2: Verify all native files are still intentionally reachable**

Run:

```bash
find app/src/main/cpp -type f | sort
```

Expected file list:

```text
app/src/main/cpp/CMakeLists.txt
app/src/main/cpp/include/custom_map_layers/maplibre/custom_layer_host.hpp
app/src/main/cpp/lib/assets/AssetReader.cpp
app/src/main/cpp/lib/assets/AssetReader.hpp
app/src/main/cpp/lib/assets/ImageDecoder.cpp
app/src/main/cpp/lib/assets/ImageDecoder.hpp
app/src/main/cpp/lib/geo/WebMercator.cpp
app/src/main/cpp/lib/geo/WebMercator.hpp
app/src/main/cpp/lib/gltf/GltfModelLoader.cpp
app/src/main/cpp/lib/gltf/GltfModelLoader.hpp
app/src/main/cpp/lib/gltf/LoadedModel.hpp
app/src/main/cpp/lib/gltf/cgltf_impl.cpp
app/src/main/cpp/lib/jni/custom_map_layers_jni.cpp
app/src/main/cpp/lib/layers/model/ModelLayer.cpp
app/src/main/cpp/lib/layers/model/ModelLayer.hpp
app/src/main/cpp/lib/rendering/GlError.cpp
app/src/main/cpp/lib/rendering/GlError.hpp
app/src/main/cpp/lib/rendering/GlTexture.cpp
app/src/main/cpp/lib/rendering/GlTexture.hpp
app/src/main/cpp/lib/rendering/GlesProgram.cpp
app/src/main/cpp/lib/rendering/GlesProgram.hpp
app/src/main/cpp/lib/rendering/VertexBuffer.cpp
app/src/main/cpp/lib/rendering/VertexBuffer.hpp
```

- [ ] **Step 3: Run final verification**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
./gradlew :app:externalNativeBuildDebug
```

Expected: both commands PASS.

- [ ] **Step 4: Check status**

Run:

```bash
git status --short
```

Expected: clean except any pre-existing user changes that were intentionally left untouched.

