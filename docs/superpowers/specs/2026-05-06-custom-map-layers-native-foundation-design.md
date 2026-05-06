# Custom Map Layers Native Foundation Design

## Goal

Prepare the Android app's native source foundation for custom MapLibre map layers while keeping the current exclamation mark layer as a temporary proof of concept.

## Scope

This design covers native source layout, CMake target naming, native library loading, and file boundaries. It does not replace the exclamation mark with production map-layer behavior. It keeps the current proof of concept available until a future task removes or replaces it.

## Architecture

The app will build one app-owned shared native library named `custom-map-layers`. Kotlin will load it with `System.loadLibrary("custom-map-layers")`, and CMake will produce `libcustom-map-layers.so`.

The native code will live under `app/src/main/cpp`. Headers that describe stable internal boundaries will live under `include/`, and implementation files will live under `lib/`. The current exclamation mark layer will move into its own `lib/layers/exclamation` package so it is clearly temporary and easy to delete later.

MapLibre's custom layer native ABI declarations will be isolated in one header. This keeps the brittle integration boundary explicit, avoids repeating local `mbgl::style` declarations, and gives future MapLibre upgrades a single place to audit.

## Native Folder Structure

```text
app/src/main/cpp/
  CMakeLists.txt

  include/
    custom_map_layers/
      maplibre/
        custom_layer_host.hpp

  lib/
    jni/
      custom_map_layers_jni.cpp

    layers/
      exclamation/
        ExclamationLayer.cpp
        ExclamationLayer.hpp

    geo/
      WebMercator.cpp
      WebMercator.hpp

    rendering/
      GlError.cpp
      GlError.hpp
      GlesProgram.cpp
      GlesProgram.hpp
      VertexBuffer.cpp
      VertexBuffer.hpp
```

## File Responsibilities

`include/custom_map_layers/maplibre/custom_layer_host.hpp` declares the local `mbgl::style::CustomLayerHost` and `CustomLayerRenderParameters` ABI used by MapLibre Android's `CustomLayer` bridge.

`lib/jni/custom_map_layers_jni.cpp` contains JNI exports only. For the proof of concept it creates and destroys `ExclamationLayer` instances. Future layer factories can be added here without mixing JNI with rendering code.

`lib/layers/exclamation/ExclamationLayer.hpp` and `ExclamationLayer.cpp` contain the temporary exclamation mark custom layer. The files own layer lifecycle callbacks and delegate reusable work to `geo` and `rendering`.

`lib/geo/WebMercator.hpp` and `WebMercator.cpp` contain deterministic Web Mercator conversion and screen projection helpers used by custom map layers.

`lib/rendering/GlError.hpp` and `GlError.cpp` contain GLES error naming and logging helpers.

`lib/rendering/GlesProgram.hpp` and `GlesProgram.cpp` contain shader compilation, program linking, and program handle ownership.

`lib/rendering/VertexBuffer.hpp` and `VertexBuffer.cpp` contain vertex buffer ownership and upload helpers.

## Build Configuration

`app/src/main/cpp/CMakeLists.txt` will define a single shared target:

```cmake
cmake_minimum_required(VERSION 4.1.2)

project(custom-map-layers LANGUAGES CXX)

add_library(custom-map-layers SHARED)

target_sources(
    custom-map-layers
    PRIVATE
        lib/jni/custom_map_layers_jni.cpp
        lib/layers/exclamation/ExclamationLayer.cpp
        lib/geo/WebMercator.cpp
        lib/rendering/GlError.cpp
        lib/rendering/GlesProgram.cpp
        lib/rendering/VertexBuffer.cpp
)

target_include_directories(
    custom-map-layers
    PRIVATE
        include
        lib
)

target_compile_features(
    custom-map-layers
    PRIVATE
        cxx_std_20
)

target_compile_options(
    custom-map-layers
    PRIVATE
        -Wall
        -Wextra
        -Wpedantic
        $<$<CONFIG:Release>:-Werror>
)

target_link_libraries(
    custom-map-layers
    PRIVATE
        android
        log
        GLESv3
)
```

`app/build.gradle.kts` will keep the existing CMake wiring, NDK version, ABI filters, and `.cxx` IDE exclusion. The native source directory remains `app/src/main/cpp`.

## Kotlin Integration

`NativeExclamationLayer` remains the Kotlin bridge for the temporary proof of concept. Its native library name changes from the generic `native-lib` to `custom-map-layers`.

The JNI function names can stay tied to `NativeExclamationLayer` while the proof of concept exists. A future task that adds multiple production layers can introduce a broader Kotlin facade such as `NativeCustomMapLayers`.

## Migration Path

1. Rename the CMake target and native library from `native-lib` to `custom-map-layers`.
2. Replace `System.loadLibrary("native-lib")` with `System.loadLibrary("custom-map-layers")`.
3. Split `native-lib.cpp` into the proposed `include/` and `lib/` files.
4. Keep behavior equivalent: the exclamation mark still renders through MapLibre's native custom layer.
5. Delete the unused `includes/` directory if it exists, because `include/` is the conventional header root.

## Validation

Run `./gradlew :app:compileDebugKotlin -q` after the native refactor. This command configures CMake and compiles Kotlin/JNI references.

Run `./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeExclamationLayerTest"` to verify the existing Kotlin bridge contract still reports the expected layer id.

Runtime validation remains manual: launch the app, load the map, verify the temporary red exclamation mark appears, move the camera, and inspect logcat for `NativeExclamationLayer` shader, render, or GLES errors.
