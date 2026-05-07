# Native Tiger Model Projection Debugging

## Issue

The custom native tiger layer did not stay visually attached to the MapLibre map surface while the user dragged to rotate the camera.

Observed behavior:

- the map surface rotated in one direction;
- the tiger model rotated in the opposite direction;
- the tiger rotated faster than the map;
- after the first attempted fix, the tiger disappeared.

Desired behavior:

- the model stands at its map coordinate like a statue;
- changing camera bearing or pitch should look like the camera moving around the model;
- the model should preserve its position relative to roads and other map features.

## Original Cause

`ModelLayer` rendered pre-projected NDC vertices:

```text
gl_Position = vec4(a_pos, 1.0)
```

Those NDC vertices came from `WebMercator::projectMetersOffsetToNdc`, which manually reimplemented camera translation, bearing, and pitch using `params.longitude`, `params.latitude`, `params.bearing`, and `params.pitch`.

That was the wrong ownership boundary. MapLibre already supplies the native custom layer with a `projectionMatrix` containing the renderer's current camera transform. The tiger was using a separate camera implementation from the map surface, so even small sign, unit, or coordinate-space mismatches became visible during rotation.

## First Attempt

The first fix moved projection into the shader:

```text
gl_Position = u_projection_matrix * vec4(a_pos, 1.0)
```

and uploaded `params.projectionMatrix` as `u_projection_matrix`.

The model vertices were converted to normalized Web Mercator coordinates:

```text
x = longitudeToMercatorX(lon)
y = latitudeToMercatorY(lat)
z = metersToMercatorUnits(altitude)
```

This addressed the important architectural problem: the map and tiger would share MapLibre's camera matrix instead of separate camera math.

## Why It Did Not Work

The model disappeared because the vertex coordinate space was wrong.

MapLibre Native's custom-layer `projectionMatrix` is produced by `TransformState::getProjMatrix`. That matrix expects world pixel coordinates at the current zoom, not normalized `0..1` Mercator coordinates.

The failed attempt fed values near `0..1` into a matrix whose x/y camera space was around tens of millions of world pixels at zoom 18. The result was effectively off-camera or too small to see.

Evidence that clarified this:

- MapLibre custom-layer render parameters include `projectionMatrix`.
- MapLibre Native builds that matrix from `TransformState::getProjMatrix`.
- `TransformState::getProjMatrix` applies camera translation in world-pixel space through `pixel_x()` and `pixel_y()`.
- Runtime logs after the final fix showed visible model bounds around `74062240..74062592` x and `42856392..42856884` y at zoom 18, which matches world-pixel scale.

## Final Fix

The final fix kept MapLibre's projection matrix, but changed model vertex input coordinates to the space MapLibre expects:

```text
worldSize = 512 * 2^zoom
worldPixelsPerMeter = metersToMercatorUnits(1, markerLatitude) * worldSize

worldX = longitudeToMercatorX(markerLongitude) * worldSize + localEastMeters * worldPixelsPerMeter
worldY = latitudeToMercatorY(markerLatitude) * worldSize - localNorthMeters * worldPixelsPerMeter
worldZ = markerAltitudeMeters + localUpMeters
```

The shader then applies:

```text
gl_Position = u_projection_matrix * vec4(worldX, worldY, worldZ, 1.0)
```

`worldZ` stays in meters because MapLibre's projection matrix scales z by meters-per-pixel internally.

## What Helped

The useful debugging step was reading MapLibre Native's own transform source instead of inferring the matrix coordinate convention from API names.

The important distinction was:

- normalized Mercator coordinates are useful as stable geographic coordinates;
- MapLibre Native's `projectionMatrix` expects zoom-scaled world pixel coordinates;
- z is treated differently from x/y and remains altitude in meters.

Runtime validation also mattered. The first compile-only check allowed a disappeared model to slip through. Installing the APK, launching on the emulator, checking `NativeModelLayer` logs, and capturing screenshots confirmed that:

- the layer initialized;
- the tiger GLB and texture loaded;
- first render emitted world-pixel bounds;
- the model was visible;
- after a drag, the model stayed visible and rotated with the map surface.

## Verification Commands

```shell
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
./gradlew :app:externalNativeBuildDebug
./gradlew :app:assembleDebug
```

Runtime verification used:

```shell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.github.arhor.journey/.ui.MainActivity
adb logcat -d -s NativeModelLayer
adb shell input swipe 800 1100 250 1100 700
adb exec-out screencap -p
```
