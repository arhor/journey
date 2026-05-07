# Native Tiger Depth And Precision Debugging

## Context

The native tiger custom layer renders `models/animal-tiger.glb` through app-owned GLES code in
`app/src/main/cpp/lib/layers/model/ModelLayer.cpp`.

Recent projection debugging established an important constraint: MapLibre Native custom-layer
`projectionMatrix` expects zoom-scaled world pixel coordinates for x/y, while z stays in meters.
Do not go back to normalized `0..1` Mercator coordinates. That previous attempt made the model
disappear because the matrix and vertex coordinate spaces did not match.

## Issue Already Investigated: See-Through Tiger From Top-Down Camera

Observed behavior:

- From the default top-down view, the tiger looked like surfaces were transparent.
- Far or internal triangles could appear over nearer body surfaces.
- Texture alpha and face culling looked suspicious at first, but were not the root cause.

Evidence:

- The fragment shader forced opaque output:

```glsl
vec4 sampled = texture(u_texture, v_uv);
fragColor = vec4(sampled.rgb, 1.0);
```

- Culling was disabled, so front-facing surfaces were not being dropped by back-face culling.
- The renderer explicitly disabled depth testing before drawing the tiger:

```cpp
glDisable(GL_DEPTH_TEST);
glDisable(GL_CULL_FACE);
glEnable(GL_BLEND);
glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
glDrawArrays(GL_TRIANGLES, 0, vertexCount_);
```

Root cause:

- The true 3D mesh was being drawn in GLB/index order instead of camera-depth order.
- For an opaque closed mesh, triangle order is not a valid visibility solution.
- This created the appearance of see-through surfaces even though alpha was forced to `1.0`.

Fix already applied:

- Added a regression test in `NativeModelLayerTest`.
- Saved the previous MapLibre depth state.
- Cleared the depth buffer for the tiger draw.
- Rendered the opaque tiger pass with:

```cpp
glDisable(GL_BLEND);
glDepthMask(GL_TRUE);
glClearDepthf(1.0f);
glClear(GL_DEPTH_BUFFER_BIT);
glEnable(GL_DEPTH_TEST);
glDepthFunc(GL_LEQUAL);
glDrawArrays(GL_TRIANGLES, 0, vertexCount_);
```

- Restored the previous depth function, clear value, depth mask, blend state, cull state, stencil state,
  array buffer, texture binding, active texture, and program before returning control to MapLibre.

Verification performed for that fix:

```shell
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
./gradlew :app:externalNativeBuildDebug
./gradlew :app:assembleDebug
```

Runtime validation:

- Installed `app/build/outputs/apk/debug/app-debug.apk` on `emulator-5554`.
- Launched `com.github.arhor.journey/.ui.MainActivity`.
- `NativeModelLayer` logs showed the GLB loaded and first render emitted world bounds.
- Screenshot `/private/tmp/journey-tiger-model-final.png` showed the default top-down see-through
  triangle-ordering problem was resolved.

## Current Issue: Blinking Holes And Disappearing Parts When Camera Moves

New observed behavior after the depth fix:

- The tiger looks good when the camera direction is top-down.
- When camera bearing/pitch changes, parts blink.
- Holes appear in different places.
- Some model parts can disappear completely.

Important constraints:

- Do not assume this is the old no-depth issue; depth testing improved the top-down case.
- Do not assume this is the old normalized-Mercator projection issue; the model is visible with current
  world-pixel coordinates.
- The next investigation should focus on what changes when camera pitch/bearing changes.

Promising hypothesis to investigate:

- The renderer currently uploads very large world-pixel x/y coordinates as `GLfloat` vertex attributes.
- Runtime world bounds are around `74062240..74062592` x and `42856392..42856884` y at zoom 18.
- At that magnitude, 32-bit float precision is coarse enough to quantize nearby vertices by several world
  pixels before the projection matrix runs on the GPU.
- When the camera rotates or pitches, this precision loss can move triangle edges and depths inconsistently,
  causing flicker, holes, or entire small parts to collapse/disappear.

Promising fix direction:

- Keep MapLibre's `projectionMatrix` as the source of camera truth, but avoid feeding large world-pixel
  coordinates to GLES as 32-bit vertex attributes.
- One focused option is to multiply `params.projectionMatrix` by each world-space vertex on the CPU in
  double precision, upload clip-space `vec4` positions as small `GLfloat` attributes, and set
  `gl_Position = a_clip_pos` in the vertex shader.
- This keeps MapLibre camera ownership, preserves GPU depth testing after perspective divide, and removes
  the large-world-coordinate precision loss at the vertex attribute boundary.

## Follow-Up Fix Attempt: CPU Double Projection To Clip Coordinates

Implemented after the current issue was reported:

- Kept MapLibre's `params.projectionMatrix` as the only camera transform.
- Added `projectWorldToClip(...)` in `ModelLayer.cpp`.
- It multiplies each world-space tiger vertex by MapLibre's projection matrix in native `double` precision.
- Changed the vertex shader from:

```glsl
gl_Position = u_projection_matrix * vec4(a_pos, 1.0);
```

to:

```glsl
layout(location = 0) in vec4 a_clip_pos;
gl_Position = a_clip_pos;
```

- Changed the vertex buffer layout from:

```text
worldX, worldY, altitudeMeters, u, v
```

to:

```text
clipX, clipY, clipZ, clipW, u, v
```

Why this is different from the failed normalized-Mercator attempt:

- The coordinate conversion still uses MapLibre's expected world-pixel x/y convention.
- The matrix still comes from MapLibre.
- The only changed boundary is where precision is lost: large world-pixel coordinates are no longer uploaded
  as 32-bit GL vertex attributes before projection.

Verification after this attempt:

```shell
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
./gradlew :app:externalNativeBuildDebug
./gradlew :app:assembleDebug
```

Runtime validation after this attempt:

- Installed the debug APK on `emulator-5554`.
- Launched `com.github.arhor.journey/.ui.MainActivity`.
- Captured `/private/tmp/journey-tiger-clip-baseline.png`.
- Performed an ADB swipe to rotate/pitch the camera.
- Captured `/private/tmp/journey-tiger-clip-after-rotate.png`.
- Filtered logs showed `NativeModelLayer` loaded and rendered with no filtered `Mbgl-MapRenderer` errors.
- The rotated/pitched screenshot did not show the obvious blinking-hole/missing-part artifact in the captured frame.

Remaining caution:

- This is still a visual bug, so one screenshot is not a complete proof for every camera path.
- If flicker remains under continuous gesture movement, collect a short sequence of screenshots or screen recording
  and compare whether failures correlate with particular pitch/bearing values.

## Notes For Future Sessions

- Runtime visual checks are required for this feature. Compile-only checks previously missed both a disappeared
  model and visual rendering defects.
- Filtered logs that matter:

```shell
adb logcat -d -s NativeModelLayer Mbgl-MapRenderer
```

- Useful screenshot command:

```shell
adb exec-out screencap -p > /private/tmp/journey-tiger-model-<label>.png
```
