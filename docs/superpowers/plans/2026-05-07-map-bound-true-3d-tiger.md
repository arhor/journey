# Map-Bound True 3D Tiger Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the screen-space tiger billboard projection with hard-coded true map-bound 3D projection.

**Architecture:** Keep the existing loader, texture, shader, and custom layer registration. Add a focused native projection helper that projects local east/north/up meter offsets around a map anchor, then update `ModelLayer` so every tiger vertex is projected from map coordinates instead of from screen pixels. Keep one hard-coded tiger and coordinate.

**Tech Stack:** Kotlin, MapLibre Android `CustomLayer`, JNI, C++20, OpenGL ES 3, Android NDK, Gradle.

---

## File Structure

Modify:

- `app/src/main/cpp/lib/geo/WebMercator.hpp`: declare local meter offset projection types/helpers.
- `app/src/main/cpp/lib/geo/WebMercator.cpp`: implement local east/north/up projection using existing Web Mercator math.
- `app/src/main/cpp/lib/layers/model/ModelLayer.cpp`: replace billboard screen-space offsetting with per-vertex map-bound projection.
- `docs/superpowers/specs/2026-05-06-native-tiger-model-layer-design.md`: append a short note that the billboard proof of concept is superseded by the true-3D step.

Do not modify:

- `GltfModelLoader`: model loading is already fixed and should stay independent of projection behavior.
- Kotlin bridge files: the layer remains hard-coded and requires no new public API in this step.

---

### Task 1: Add Local Meter Offset Projection Helper

**Files:**
- Modify: `app/src/main/cpp/lib/geo/WebMercator.hpp`
- Modify: `app/src/main/cpp/lib/geo/WebMercator.cpp`

- [ ] **Step 1: Add projection types to the header**

In `app/src/main/cpp/lib/geo/WebMercator.hpp`, add these declarations after `struct ScreenPoint`:

```cpp
struct LocalMeters {
    double east;
    double north;
    double up;
};
```

Add this function declaration after `projectToNdc(...)`:

```cpp
ScreenPoint projectMetersOffsetToNdc(
        double originLongitude,
        double originLatitude,
        double originAltitudeMeters,
        LocalMeters offsetMeters,
        const mbgl::style::CustomLayerRenderParameters& parameters
);
```

- [ ] **Step 2: Implement the helper**

In `app/src/main/cpp/lib/geo/WebMercator.cpp`, add this implementation after `projectToNdc(...)`:

```cpp
ScreenPoint projectMetersOffsetToNdc(
        double originLongitude,
        double originLatitude,
        double originAltitudeMeters,
        LocalMeters offsetMeters,
        const mbgl::style::CustomLayerRenderParameters& params
) {
    const double worldSize = kTileSize * std::pow(2.0, params.zoom);
    const double worldUnitsPerMeter = metersToMercatorUnits(1.0, originLatitude) * worldSize;

    const double cameraX = longitudeToMercatorX(params.longitude) * worldSize;
    const double cameraY = latitudeToMercatorY(params.latitude) * worldSize;

    const double originX = longitudeToMercatorX(originLongitude) * worldSize;
    const double originY = latitudeToMercatorY(originLatitude) * worldSize;

    const double pointX = originX + offsetMeters.east * worldUnitsPerMeter;
    const double pointY = originY - offsetMeters.north * worldUnitsPerMeter;
    const double pointAltitudePixels =
            (originAltitudeMeters + offsetMeters.up) * worldUnitsPerMeter;

    const double bearingRadians = degreesToRadiansIfNeeded(params.bearing);
    const double pitchRadians = degreesToRadiansIfNeeded(params.pitch);

    const double dx = pointX - cameraX;
    const double dy = pointY - cameraY;

    const double rotatedX = dx * std::cos(bearingRadians) - dy * std::sin(bearingRadians);
    const double rotatedY = dx * std::sin(bearingRadians) + dy * std::cos(bearingRadians);

    const double pitchedY =
            rotatedY * std::cos(pitchRadians) - pointAltitudePixels * std::sin(pitchRadians);

    return ScreenPoint{
            .x = 2.0 * rotatedX / params.width,
            .y = -2.0 * pitchedY / params.height,
    };
}
```

- [ ] **Step 3: Build native code**

Run:

```shell
./gradlew :app:externalNativeBuildDebug
```

Expected: build succeeds.

- [ ] **Step 4: Commit**

```shell
git add app/src/main/cpp/lib/geo/WebMercator.hpp app/src/main/cpp/lib/geo/WebMercator.cpp
git commit -m "Add native local meter map projection helper"
```

---

### Task 2: Project Tiger Vertices From Map Coordinates

**Files:**
- Modify: `app/src/main/cpp/lib/layers/model/ModelLayer.cpp`

- [ ] **Step 1: Replace billboard constants**

In `app/src/main/cpp/lib/layers/model/ModelLayer.cpp`, replace:

```cpp
constexpr double kModelScaleMeters = 45.0;
constexpr double kBillboardVerticalOffsetNdc = -0.15;
```

with:

```cpp
constexpr double kMarkerAltitudeMeters = 0.0;
constexpr double kModelMetersPerUnit = 45.0;
constexpr double kModelHeadingRadians = 0.0;
```

- [ ] **Step 2: Add a local heading helper**

In the anonymous namespace in `ModelLayer.cpp`, after `struct NdcBounds`, add:

```cpp
custom_map_layers::geo::LocalMeters rotateLocalModelMeters(
        const custom_map_layers::gltf::ModelVertex& vertex
) {
    const double modelEast = static_cast<double>(vertex.x) * kModelMetersPerUnit;
    const double modelNorth = static_cast<double>(-vertex.z) * kModelMetersPerUnit;
    const double modelUp = static_cast<double>(vertex.y) * kModelMetersPerUnit;

    const double cosHeading = std::cos(kModelHeadingRadians);
    const double sinHeading = std::sin(kModelHeadingRadians);

    return custom_map_layers::geo::LocalMeters{
            .east = modelEast * cosHeading - modelNorth * sinHeading,
            .north = modelEast * sinHeading + modelNorth * cosHeading,
            .up = modelUp,
    };
}
```

Add `#include <cmath>` near the other standard includes because the helper uses `std::cos` and `std::sin`.

- [ ] **Step 3: Replace `buildProjectedVertices`**

Replace the body of `ModelLayer::buildProjectedVertices(...)` with:

```cpp
std::vector<GLfloat> vertices;
vertices.reserve(model_.triangleVertices.size() * 5);

for (const custom_map_layers::gltf::ModelVertex& vertex : model_.triangleVertices) {
    const custom_map_layers::geo::LocalMeters localMeters = rotateLocalModelMeters(vertex);
    const custom_map_layers::geo::ScreenPoint projected =
            custom_map_layers::geo::projectMetersOffsetToNdc(
                    kMarkerLongitude,
                    kMarkerLatitude,
                    kMarkerAltitudeMeters,
                    localMeters,
                    params
            );

    vertices.push_back(static_cast<GLfloat>(projected.x));
    vertices.push_back(static_cast<GLfloat>(projected.y));
    vertices.push_back(0.0f);
    vertices.push_back(vertex.u);
    vertices.push_back(vertex.v);
}

return vertices;
```

This removes the current screen-space math:

```cpp
const custom_map_layers::geo::ScreenPoint marker = ...
const double horizontalNdcPerMeter = ...
const double verticalNdcPerMeter = ...
```

- [ ] **Step 4: Update the first-render log**

In the first-render log format string, change:

```cpp
"render %.0fx%.0f camera=(%.7f, %.7f) zoom=%.2f bearing=%.4f pitch=%.4f marker=(%.7f, %.7f) vertices=%d"
```

to:

```cpp
"render %.0fx%.0f camera=(%.7f, %.7f) zoom=%.2f bearing=%.4f pitch=%.4f marker=(%.7f, %.7f, %.1fm) vertices=%d"
```

Add `kMarkerAltitudeMeters` to the argument list between `kMarkerLongitude` and `vertexCount_`.

- [ ] **Step 5: Build native code**

Run:

```shell
./gradlew :app:externalNativeBuildDebug
```

Expected: build succeeds.

- [ ] **Step 6: Commit**

```shell
git add app/src/main/cpp/lib/layers/model/ModelLayer.cpp
git commit -m "Render tiger vertices from map coordinates"
```

---

### Task 3: Validate App Build And Runtime Behavior

**Files:**
- No source changes expected unless runtime validation exposes a defect.

- [ ] **Step 1: Run focused JVM tests**

Run:

```shell
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
```

Expected: build succeeds and the test class passes.

- [ ] **Step 2: Assemble debug APK**

Run:

```shell
./gradlew :app:assembleDebug
```

Expected: build succeeds.

- [ ] **Step 3: Install debug APK**

Run:

```shell
/Users/maksimburyshynets/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`.

- [ ] **Step 4: Launch with clean logs**

Run:

```shell
/Users/maksimburyshynets/Library/Android/sdk/platform-tools/adb logcat -c
/Users/maksimburyshynets/Library/Android/sdk/platform-tools/adb shell am force-stop com.github.arhor.journey
/Users/maksimburyshynets/Library/Android/sdk/platform-tools/adb shell monkey -p com.github.arhor.journey 1
```

Expected: app launches to the map screen.

- [ ] **Step 5: Capture and inspect logs**

Run:

```shell
/Users/maksimburyshynets/Library/Android/sdk/platform-tools/adb logcat -d -s NativeModelLayer Mbgl-MapRenderer
```

Expected:

- `Loaded tiger model vertices=2853 texture=models/textures/colormap.png`
- first render log includes marker altitude
- projected bounds are finite numbers
- no `std::bad_alloc`
- no GLES error logs from `NativeModelLayer`

- [ ] **Step 6: Capture a screenshot**

Run:

```shell
/bin/zsh -lc '/Users/maksimburyshynets/Library/Android/sdk/platform-tools/adb exec-out screencap -p > /private/tmp/journey-tiger-model-final.png'
```

Open `/private/tmp/journey-tiger-model-final.png`.

Expected at pitch `0`: the tiger may look like a top-down footprint, not a readable side marker. It must not remain visually pinned to the center when the map is moved.

- [ ] **Step 7: Manual map interaction check**

On the emulator:

1. Pan the map so the hard-coded coordinate moves away from screen center.
2. Confirm the tiger moves with the map point instead of staying near screen center.
3. Zoom in and out.
4. Confirm the tiger scales with the map.
5. Rotate the map.
6. Confirm the tiger footprint rotates with the map.
7. Pitch the map.
8. Confirm altitude creates a standing 3D shape instead of a screen billboard.

- [ ] **Step 8: Commit validation-only follow-up if needed**

If no code changes were needed, do not commit.

If a runtime fix was required, commit only the changed files:

```shell
git add app/src/main/cpp/lib/geo/WebMercator.hpp app/src/main/cpp/lib/geo/WebMercator.cpp app/src/main/cpp/lib/layers/model/ModelLayer.cpp
git commit -m "Tune map-bound tiger projection"
```

---

### Task 4: Update Documentation

**Files:**
- Modify: `docs/superpowers/specs/2026-05-06-native-tiger-model-layer-design.md`
- Modify: `docs/superpowers/specs/2026-05-07-map-bound-true-3d-tiger-design.md`

- [ ] **Step 1: Update the original tiger layer design**

In `docs/superpowers/specs/2026-05-06-native-tiger-model-layer-design.md`, add this paragraph after the `Integration Issues And Fixes` section:

```markdown
The later map-bound true-3D step supersedes the screen-space billboard projection described here. The billboard implementation was useful for proving GLB loading, texture upload, and custom-layer drawing, but true model placement now projects every tiger vertex from local east/north/up meters around the hard-coded map coordinate.
```

- [ ] **Step 2: Update the true-3D design with implementation notes**

In `docs/superpowers/specs/2026-05-07-map-bound-true-3d-tiger-design.md`, add this section before `## Follow-Up Work`:

```markdown
## Implementation Notes

The first implementation uses the existing native Web Mercator camera helper rather than directly multiplying `params.projectionMatrix`. This keeps the behavior consistent with the previous native custom layer projection path while still making every model vertex map-bound. A later rendering pass can replace the CPU projection helper with direct projection-matrix uniforms once the MapLibre native matrix coordinate convention is validated with screenshots and logs.
```

- [ ] **Step 3: Check documentation formatting**

Run:

```shell
git diff --check
```

Expected: no output.

- [ ] **Step 4: Commit docs**

```shell
git add docs/superpowers/specs/2026-05-06-native-tiger-model-layer-design.md docs/superpowers/specs/2026-05-07-map-bound-true-3d-tiger-design.md
git commit -m "Document map-bound tiger projection implementation"
```

---

## Final Verification

Run:

```shell
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
./gradlew :app:assembleDebug
git status --short
```

Expected:

- focused JVM test passes
- debug APK assembles
- working tree is clean except for any intentional uncommitted runtime screenshots outside the repo

Runtime evidence to report:

- filtered `NativeModelLayer` / `Mbgl-MapRenderer` log summary
- screenshot path used for final visual check
- whether panning, zooming, rotating, and pitching confirmed map-bound behavior
