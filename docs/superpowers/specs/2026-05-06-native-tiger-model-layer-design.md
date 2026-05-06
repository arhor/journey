# Native Tiger Model Layer Design

## Goal

Replace the temporary native exclamation mark map layer with a native custom layer that renders the bundled tiger GLB at hard-coded map coordinates.

The first implementation should prove that the Android app can load and render a real glTF model inside MapLibre's native `CustomLayer` lifecycle. It should keep the scope narrow, but the file boundaries and data structures should make future work on multiple model assets and dynamic marker locations straightforward.

## Scope

This design covers one hard-coded tiger model marker:

- Model asset: `app/src/main/assets/models/animal-tiger.glb`
- Texture asset fallback: `app/src/main/assets/models/textures/colormap.png`
- Map location: the same hard-coded coordinates currently used by the exclamation proof of concept
- Renderer: app-owned GLES code inside `libcustom-map-layers.so`

This design does not cover gameplay-driven model selection, multiple model instances, animation, lighting beyond a simple textured shader, picking, collision, or ViewModel state changes for model markers.

## Architecture

The existing Kotlin `NativeExclamationLayer` bridge will be replaced with `NativeModelLayer`. It will still add a MapLibre Android `CustomLayer`, but its JNI context creation will receive `context.assets` so native code can read bundled assets through Android's native asset APIs.

The existing native library target remains `custom-map-layers`. The exclamation-specific native layer will be replaced by a model layer package that owns MapLibre lifecycle callbacks and delegates asset loading, glTF parsing, image decoding, and GLES resource ownership to focused helpers.

The native implementation will use `cgltf` for glTF parsing instead of manually parsing GLB JSON and binary chunks. `cgltf` handles the glTF structure, including buffers, buffer views, accessors, indexed primitives, materials, nodes, and scene traversal. Android-specific code will still resolve external resources from `AAssetManager`, because `cgltf` does not automatically load external files or images by default.

## Native Dependencies

Vendor `cgltf.h` under:

```text
app/src/main/cpp/third_party/cgltf/cgltf.h
```

Add one implementation translation unit for the single-header library:

```text
app/src/main/cpp/lib/gltf/cgltf_impl.cpp
```

The CMake target will compile the new files and include `third_party/cgltf`. It will continue linking `android`, `log`, and `GLESv3`, and will add `jnigraphics` for native PNG decoding through `AImageDecoder`.

## Native Folder Structure

```text
app/src/main/cpp/
  CMakeLists.txt

  third_party/
    cgltf/
      cgltf.h

  lib/
    assets/
      AssetReader.cpp
      AssetReader.hpp
      ImageDecoder.cpp
      ImageDecoder.hpp

    gltf/
      GltfModelLoader.cpp
      GltfModelLoader.hpp
      LoadedModel.hpp
      cgltf_impl.cpp

    layers/
      model/
        ModelLayer.cpp
        ModelLayer.hpp

    rendering/
      GlTexture.cpp
      GlTexture.hpp
      GlesProgram.cpp
      GlesProgram.hpp
      VertexBuffer.cpp
      VertexBuffer.hpp
```

The existing `geo` helpers remain available for map projection. The existing exclamation package can be deleted once the model layer replaces it.

## File Responsibilities

`NativeModelLayer.kt` loads `custom-map-layers`, creates the native context with `context.assets`, adds a `CustomLayer`, and destroys the native context if layer creation fails before MapLibre takes ownership.

`lib/jni/custom_map_layers_jni.cpp` exposes `NativeModelLayer_createContextNative(assetManager)` and `NativeModelLayer_destroyContextNative(context)`. It converts the Java `AssetManager` to `AAssetManager*` with `AAssetManager_fromJava`.

`AssetReader` reads full asset files into memory with `AAssetManager_open`, `AAsset_getLength64`, and `AAsset_read`. It is responsible for logging missing assets and returning explicit failure values.

`ImageDecoder` decodes PNG bytes into RGBA pixels with `AImageDecoder`. The model layer will upload those pixels into an OpenGL texture.

`GltfModelLoader` reads `models/animal-tiger.glb`, parses it with `cgltf_parse`, validates that the model contains the supported primitive data, applies node transforms needed by the tiger scene, resolves primitive attributes through `cgltf_accessor`, and emits app-owned `LoadedModel` data.

`LoadedModel` contains flattened CPU-side primitives suitable for rendering: positions, UVs, indices, material texture references, and model bounds. The first implementation may omit normals and tangents because the shader is unlit and texture-only.

`ModelLayer` owns MapLibre custom layer callbacks. It loads CPU model data, creates GLES buffers and texture resources in `initialize()`, renders at the hard-coded coordinates in `render()`, handles `contextLost()` by forgetting GLES handles, and releases resources in `deinitialize()`.

`GlTexture` owns texture creation, upload, binding, reset, and context-loss forgetting. Existing `GlesProgram` and `VertexBuffer` stay as reusable rendering helpers.

## Asset Resolution

The GLB references its texture as `Textures/colormap.png`, while the repository currently contains `models/textures/colormap.png`. The first implementation should resolve texture URIs relative to the model path, then apply a compatibility fallback from `Textures/colormap.png` to `models/textures/colormap.png`.

This keeps the current asset usable without silently ignoring the model's material. A later asset-cleanup task can normalize exported paths and remove the fallback.

## Rendering Behavior

The model should render at the same latitude and longitude used by the existing exclamation layer:

```text
lat = 54.3744505
lon = 18.6502754
```

The first implementation should use a fixed model scale chosen to make the tiger visible on the map at the current test zoom range. Orientation can initially be fixed in world space. The renderer should leave scale and orientation constants close to `ModelLayer`, so they can become per-instance data in a later generalized marker system.

The shader should be simple:

- Vertex inputs: position and UV
- Uniforms: map/model transform matrix or equivalent projected coordinates, texture sampler
- Fragment output: sampled base-color texture

The layer should set only the GL state it needs and restore enough state to avoid polluting MapLibre rendering. Depth testing can be disabled for the first pass if needed for visibility; if enabled, it must be paired with predictable ordering and clear documentation.

## Error Handling

Native failures should log actionable messages under a `NativeModelLayer` tag:

- Missing GLB asset
- `cgltf_parse` failure
- Unsupported primitive component type or attribute layout
- Missing texture and failed fallback
- PNG decode failure
- Shader, buffer, or texture upload failure

If loading fails, the layer should skip rendering rather than crash the app. JNI context creation may still return a valid layer object, because `initialize()` is the first callback with a valid GL context.

## Testing

JVM tests should cover the Kotlin bridge contract:

- `NativeModelLayer.layerId()` returns the expected model layer id.
- `addToWithManagedContext` destroys the context and rethrows when MapLibre layer creation fails before ownership transfer.

Native parsing and rendering will be validated by build and runtime checks in this step. The implementation should keep model loading code small enough that focused native tests or host-side fixture tests can be added later if the native test setup is expanded.

## Validation

After implementation, run:

```shell
./gradlew :app:compileDebugKotlin -q
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
```

Runtime validation:

1. Launch the app and open the map.
2. Verify the exclamation mark no longer renders.
3. Verify the tiger model renders at the hard-coded coordinate.
4. Move, zoom, rotate, and pitch the map.
5. Inspect logcat for `NativeModelLayer` asset, shader, texture, or GLES errors.

## Future Generalization

The next step can move hard-coded asset path, coordinate, scale, and orientation into model marker instance data. The same `LoadedModel`, `GlTexture`, and `ModelLayer` boundaries should support:

- Multiple coordinates using one loaded model
- Different model assets
- ViewModel-driven marker snapshots
- Per-marker scale and heading
- Model cache reuse across style reloads or layer recreation
