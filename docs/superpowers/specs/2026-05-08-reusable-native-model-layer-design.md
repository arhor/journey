# Reusable Native Model Layer Design

## Goal

Make the native `ModelLayer` reusable by moving model selection and placement out of hard-coded C++ constants and into
Kotlin-provided data.

For this step, Kotlin will pass a fixed list of models when the MapLibre custom layer is attached. The native layer
should still be shaped so a later `updateModels` API can replace that list without rewriting rendering logic.

## Current State

`NativeModelLayer.addTo(...)` only passes `AssetManager` to JNI. The native `ModelLayer` then hard-codes:

- model path: `models/animal-tiger.glb`
- marker latitude and longitude
- marker altitude
- model scale
- model heading
- texture fallback behavior inside `GltfModelLoader`

The renderer already uses MapLibre's `projectionMatrix` and projects each vertex from local east, north, and up meters
around the map anchor. That projection boundary should remain. The reusable work is mainly data plumbing, asset loading,
and per-instance transform ownership.

## Recommended Approach

Use one reusable native custom layer with a Kotlin-provided list of model specs.

Kotlin should define an immutable spec similar to:

```kotlin
internal data class NativeMapModelSpec(
    val assetPath: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 0.0,
    val scaleMetersPerModelUnit: Double,
    val headingDegrees: Double = 0.0,
)
```

`NativeModelLayer.addTo(...)` should accept `models: List<NativeMapModelSpec>`. The current map screen can pass one
tiger spec matching today's behavior. Other models can be added by appending specs with different asset paths and
locations.

`altitudeMeters` and `scaleMetersPerModelUnit` must remain separate:

- `altitudeMeters` moves the model anchor vertically on the map.
- `scaleMetersPerModelUnit` converts GLB local vertex units into real-world meters.

Combining them would couple placement and size. A model should be able to sit on the ground while still being scaled up,
or float above the ground while keeping its original scale.

## Native Architecture

Introduce a native model instance spec that mirrors the Kotlin data:

- `assetPath`
- `latitude`
- `longitude`
- `altitudeMeters`
- `scaleMetersPerModelUnit`
- `headingRadians`

Change `ModelLayer` construction from `ModelLayer(AAssetManager*)` to `ModelLayer(AAssetManager*, instances)`.

Change `GltfModelLoader` from a tiger-specific API to a path-based API:

```cpp
std::optional<LoadedModel> load(const std::string& assetPath, const char* logTag) const;
```

`ModelLayer` should keep:

- the provided instance list;
- a cache of loaded model resources keyed by `assetPath`;
- the existing shader, depth, projection, and GL state restoration behavior.

Each render should loop over model instances. For each instance, the layer should:

1. find the cached loaded model for the instance asset;
2. convert model-space vertices into local east, north, and up meters using `scaleMetersPerModelUnit` and heading;
3. anchor those meters at the instance latitude, longitude, and altitude;
4. project to clip coordinates with MapLibre's `projectionMatrix`;
5. draw the instance.

The first implementation can continue uploading projected vertices per frame. That preserves the existing precision fix
and avoids prematurely adding GPU instancing or model matrices.

## Asset Loading And Textures

The native cache should load each unique `assetPath` once per GL context initialization. Multiple instances using the
same model asset should share loaded CPU model data and texture resources.

The loader can keep the existing texture URI resolution behavior for the bundled tiger, but it should no longer assume
the model itself is always `models/animal-tiger.glb`. Texture fallback can remain `models/textures/colormap.png` until
material support is improved.

Missing or invalid model assets should be logged with the asset path. The layer should skip failed assets rather than
crashing the map renderer.

## Future Update Path

The initial API is fixed-at-attach:

```kotlin
NativeModelLayer.addTo(map, style, assetManager, models)
```

To support runtime updates later, add:

```kotlin
NativeModelLayer.updateModels(context, models)
```

The later update should reuse the same Kotlin spec and JNI conversion code. Native code can replace the instance list,
load missing assets, keep cached resources for unchanged asset paths, and let Kotlin trigger a repaint.

This design avoids one custom layer per model, so future updates do not require adding and removing many MapLibre layers.

## Alternatives Considered

### One Custom Layer Per Model

This is simple to wire because each layer owns one asset and one transform. It is not recommended because it scales
poorly: each model adds a MapLibre custom-layer lifecycle, repeats GL state save and restore work, and makes asset
caching harder to centralize.

### MapLibre Style Data As Transport

Encoding model markers into style data or GeoJSON would align with other map primitives, but the custom GLES renderer
still needs native meshes, textures, and transforms. This would add an indirect transport layer without removing native
state management.

## Testing

Add or update focused JVM tests around the Kotlin and native source boundaries:

- `NativeModelLayer.addToWithManagedContext` passes model specs to context creation.
- `NativeModelLayer.layerId()` remains stable.
- native source no longer hard-codes tiger location, scale, or heading constants in `ModelLayer.cpp`;
- `GltfModelLoader` exposes a path-based loading API instead of `loadTiger`;
- existing projection and depth tests continue to verify MapLibre projection matrix use and opaque 3D depth testing.

Runtime validation should install the app, open the map, and confirm that the Kotlin-provided tiger spec still renders at
the expected coordinate with no `NativeModelLayer` or `Mbgl-MapRenderer` errors.
