# Map-Bound True 3D Tiger Design

## Goal

Change the native tiger layer from a screen-space billboard into a true map-bound 3D model. The tiger should stand at a specific latitude and longitude, move with the map when the user pans, rotate with the map bearing, scale with zoom, and reveal its 3D shape when the map is pitched.

This step keeps the marker hard-coded. It does not introduce gameplay-driven marker data, multiple models, animation, lighting, picking, or generalized model caching.

## Current Problem

`ModelLayer` currently projects one marker coordinate to NDC, then adds model `x/y` offsets directly in screen space:

```text
screen_position = projected_marker + model_offset_in_pixels
```

That makes the tiger readable, but it behaves like it is stuck to the camera surface. The marker coordinate only anchors the center point; the model geometry itself is not part of the map/world transform.

## Decision

Move directly to true 3D world projection for the hard-coded tiger.

Each GLB vertex will be treated as local model-space meters around the marker anchor:

- model `x`: local east/west offset
- model `z`: local north/south offset
- model `y`: local up/altitude offset

For each frame, native code will convert the anchor coordinate and each local model offset into map/world coordinates, then project that world position through the native map projection path. The first implementation uses CPU-projected clip/NDC coordinates; a later rendering pass can move this into shader uniforms once the MapLibre custom layer projection-matrix convention is validated.

At pitch `0`, the tiger may look mostly like a top-down footprint. That is acceptable for this step because the requirement is physical map binding, not billboard readability. Runtime validation should include pitched and rotated map views to verify the model stands on the map.

## Rendering Model

The implementation should replace the billboard calculation in `ModelLayer::buildProjectedVertices`.

Preferred first implementation:

1. Convert the marker longitude and latitude to Mercator coordinates.
2. Convert local model east/north/up meter offsets into Mercator/world units at the marker latitude.
3. Apply the current zoom/world-size convention used by the native Web Mercator helper.
4. Transform each vertex through the same bearing, pitch, and NDC math used by the native custom layer projection path.
5. Emit projected position plus UV to the existing GLES vertex buffer.

The current shader can remain simple: position plus UV in, sampled base-color texture out with opaque alpha. Lighting and depth can stay minimal, but depth testing should be reconsidered during validation. If depth is disabled, the model will always draw over the map. If depth is enabled, ordering against MapLibre terrain/buildings is outside this step.

## Coordinate Constants

Keep one hard-coded marker for now:

```text
lat = 54.3738000
lon = 18.6508750
altitude = 0m
```

Keep scale and orientation as local constants in `ModelLayer`. They should be named as marker/model configuration so a later step can lift them into instance data without rewriting projection logic.

## Diagnostics

The first render should continue logging enough data to debug placement:

- camera lat/lon, zoom, bearing, and pitch
- marker lat/lon/altitude
- projected clip or NDC bounds
- model vertex count

During development, a temporary solid-color shader or axes helper is acceptable for proving placement, but the committed implementation should render the textured tiger.

## Validation

Build and test:

```shell
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
./gradlew :app:assembleDebug
```

Runtime validation:

1. Launch the app on the map.
2. Verify the tiger is located at the hard-coded coordinate, not fixed near screen center.
3. Pan the map so the coordinate moves away from the center; the tiger should move with that map point.
4. Zoom in and out; the tiger should scale consistently with the map.
5. Rotate the map; the tiger footprint should rotate with the map.
6. Pitch the map; the tiger should reveal vertical 3D shape instead of remaining a flat screen billboard.
7. Inspect `NativeModelLayer` and `Mbgl-MapRenderer` logs for GLES errors or renderer failures.

## Implementation Notes

The first implementation uses the existing native Web Mercator camera helper rather than directly multiplying `params.projectionMatrix`. This keeps the behavior consistent with the previous native custom layer projection path while still making every model vertex map-bound. A later rendering pass can replace the CPU projection helper with direct projection-matrix uniforms once the MapLibre native matrix coordinate convention is validated with screenshots and logs.

## Follow-Up Work

After this hard-coded true-3D step, the next natural generalization is to replace constants with model marker instance data:

- marker coordinate and altitude
- model asset id
- scale
- heading/orientation
- render mode, if later we want both true 3D models and readable billboard markers
