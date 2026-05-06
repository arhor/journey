# Custom Map Layers Native Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the current native MapLibre proof of concept into one app-owned `custom-map-layers` native library with a maintainable C++ folder structure.

**Architecture:** Keep one shared CMake target that produces `libcustom-map-layers.so`. Move reusable native code into focused `include/` and `lib/` units while keeping the temporary exclamation layer behavior equivalent.

**Tech Stack:** Android Gradle Plugin, CMake 4.1.2, Android NDK 29, C++20, JNI, OpenGL ES 3.0, MapLibre Android OpenGL custom layers.

---

## File Structure

- Modify: `app/src/main/cpp/CMakeLists.txt` - define the `custom-map-layers` target and list all split source files.
- Delete: `app/src/main/cpp/native-lib.cpp` - replace the monolithic proof-of-concept source after extraction.
- Delete: `app/src/main/cpp/includes/` - remove the unused nonstandard header directory if it is still present and empty.
- Create: `app/src/main/cpp/include/custom_map_layers/maplibre/custom_layer_host.hpp` - isolate MapLibre native custom layer ABI declarations.
- Create: `app/src/main/cpp/lib/jni/custom_map_layers_jni.cpp` - own JNI exports only.
- Create: `app/src/main/cpp/lib/layers/exclamation/ExclamationLayer.hpp` - declare the temporary layer class.
- Create: `app/src/main/cpp/lib/layers/exclamation/ExclamationLayer.cpp` - implement the temporary layer behavior.
- Create: `app/src/main/cpp/lib/geo/WebMercator.hpp` - declare projection helpers.
- Create: `app/src/main/cpp/lib/geo/WebMercator.cpp` - implement projection helpers.
- Create: `app/src/main/cpp/lib/rendering/GlError.hpp` - declare GLES error logging helpers.
- Create: `app/src/main/cpp/lib/rendering/GlError.cpp` - implement GLES error logging helpers.
- Create: `app/src/main/cpp/lib/rendering/GlesProgram.hpp` - declare shader/program ownership.
- Create: `app/src/main/cpp/lib/rendering/GlesProgram.cpp` - implement shader/program ownership.
- Create: `app/src/main/cpp/lib/rendering/VertexBuffer.hpp` - declare vertex buffer ownership.
- Create: `app/src/main/cpp/lib/rendering/VertexBuffer.cpp` - implement vertex buffer ownership.
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeExclamationLayer.kt` - load `custom-map-layers`.

---

### Task 1: Rename Native Library Target

**Files:**
- Modify: `app/src/main/cpp/CMakeLists.txt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeExclamationLayer.kt`
- Test: `app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeExclamationLayerTest.kt`

- [ ] **Step 1: Update the CMake target name**

Replace the current `native-lib` target with the final app-owned target name:

```cmake
cmake_minimum_required(VERSION 4.1.2)

project(custom-map-layers LANGUAGES CXX)

add_library(
        custom-map-layers
        SHARED
        native-lib.cpp
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

- [ ] **Step 2: Update Kotlin library loading**

In `NativeExclamationLayer.kt`, load the renamed shared library:

```kotlin
init {
    System.loadLibrary("custom-map-layers")
}
```

- [ ] **Step 3: Compile the renamed native target**

Run:

```bash
./gradlew :app:compileDebugKotlin -q
```

Expected: build succeeds, CMake configures `custom-map-layers`, and no `UnsatisfiedLinkError`-related compile issue appears.

- [ ] **Step 4: Run the existing bridge test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeExclamationLayerTest"
```

Expected: the test passes and `layerId()` still returns `native-exclamation-layer`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/cpp/CMakeLists.txt app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeExclamationLayer.kt
git commit -m "refactor: rename native custom map layers library"
```

---

### Task 2: Isolate MapLibre Custom Layer ABI

**Files:**
- Create: `app/src/main/cpp/include/custom_map_layers/maplibre/custom_layer_host.hpp`
- Modify: `app/src/main/cpp/native-lib.cpp`

- [ ] **Step 1: Create the ABI header**

Create `app/src/main/cpp/include/custom_map_layers/maplibre/custom_layer_host.hpp`:

```cpp
#pragma once

#include <array>

namespace mbgl::style {

struct CustomLayerRenderParameters {
    double width;
    double height;
    double latitude;
    double longitude;
    double zoom;
    double bearing;
    double pitch;
    double fieldOfView;
    std::array<double, 16> projectionMatrix;
};

class CustomLayerHost {
public:
    virtual ~CustomLayerHost() = default;

    virtual void initialize() = 0;

    virtual void render(const CustomLayerRenderParameters& parameters) = 0;

    virtual void contextLost() = 0;

    virtual void deinitialize() = 0;
};

} // namespace mbgl::style
```

- [ ] **Step 2: Include the ABI header from the monolith**

In `native-lib.cpp`, remove the local `namespace mbgl::style` declaration block and include the new header:

```cpp
#include "custom_map_layers/maplibre/custom_layer_host.hpp"
```

- [ ] **Step 3: Compile the ABI extraction**

Run:

```bash
./gradlew :app:compileDebugKotlin -q
```

Expected: build succeeds with the custom layer ABI coming from the header.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/cpp/include/custom_map_layers/maplibre/custom_layer_host.hpp app/src/main/cpp/native-lib.cpp
git commit -m "refactor: isolate MapLibre custom layer ABI"
```

---

### Task 3: Extract Rendering Utilities

**Files:**
- Create: `app/src/main/cpp/lib/rendering/GlError.hpp`
- Create: `app/src/main/cpp/lib/rendering/GlError.cpp`
- Create: `app/src/main/cpp/lib/rendering/GlesProgram.hpp`
- Create: `app/src/main/cpp/lib/rendering/GlesProgram.cpp`
- Create: `app/src/main/cpp/lib/rendering/VertexBuffer.hpp`
- Create: `app/src/main/cpp/lib/rendering/VertexBuffer.cpp`
- Modify: `app/src/main/cpp/native-lib.cpp`

- [ ] **Step 1: Create GLES error helper declarations**

Create `GlError.hpp`:

```cpp
#pragma once

namespace custom_map_layers::rendering {

void logGlErrors(const char* operation, const char* logTag);

} // namespace custom_map_layers::rendering
```

- [ ] **Step 2: Move GLES error implementation**

Create `GlError.cpp` by moving the existing `glErrorName` and `logGlErrors` implementation from `native-lib.cpp`, keeping the same Android log behavior and changing the public function signature to:

```cpp
void logGlErrors(const char* operation, const char* logTag);
```

The implementation must call:

```cpp
__android_log_print(ANDROID_LOG_ERROR, logTag, "%s: %s", operation, glErrorName(error));
```

- [ ] **Step 3: Create GLES program declarations**

Create `GlesProgram.hpp`:

```cpp
#pragma once

#include <GLES3/gl3.h>

namespace custom_map_layers::rendering {

class GlesProgram {
public:
    GlesProgram() = default;
    ~GlesProgram();

    GlesProgram(const GlesProgram&) = delete;
    GlesProgram& operator=(const GlesProgram&) = delete;

    bool create(const char* vertexShaderSource, const char* fragmentShaderSource, const char* logTag);
    void reset();

    [[nodiscard]] GLuint handle() const;

private:
    GLuint program_ = 0;
    GLuint vertexShader_ = 0;
    GLuint fragmentShader_ = 0;
};

} // namespace custom_map_layers::rendering
```

- [ ] **Step 4: Move shader and program implementation**

Create `GlesProgram.cpp` by moving the existing shader compile, shader check, program check, attach, link, detach, and delete behavior from `native-lib.cpp`.

The implementation must preserve these behaviors:

```cpp
bool GlesProgram::create(const char* vertexShaderSource, const char* fragmentShaderSource, const char* logTag);
void GlesProgram::reset();
GLuint GlesProgram::handle() const;
```

`reset()` must delete the program and shaders if their handles are nonzero, then set all handles to `0`.

- [ ] **Step 5: Create vertex buffer declarations**

Create `VertexBuffer.hpp`:

```cpp
#pragma once

#include <GLES3/gl3.h>

#include <vector>

namespace custom_map_layers::rendering {

class VertexBuffer {
public:
    VertexBuffer() = default;
    ~VertexBuffer();

    VertexBuffer(const VertexBuffer&) = delete;
    VertexBuffer& operator=(const VertexBuffer&) = delete;

    bool create(const char* logTag);
    void upload(const std::vector<GLfloat>& vertices) const;
    void bind() const;
    void reset();

    [[nodiscard]] GLuint handle() const;

private:
    GLuint buffer_ = 0;
};

} // namespace custom_map_layers::rendering
```

- [ ] **Step 6: Move vertex buffer implementation**

Create `VertexBuffer.cpp` with `glGenBuffers`, `glBindBuffer`, `glBufferData`, and `glDeleteBuffers` ownership. `upload()` must use `GL_DYNAMIC_DRAW`, matching the proof of concept.

- [ ] **Step 7: Compile rendering extraction**

Run:

```bash
./gradlew :app:compileDebugKotlin -q
```

Expected: build succeeds and no duplicate symbol errors remain from the moved rendering functions.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/cpp/lib/rendering app/src/main/cpp/native-lib.cpp
git commit -m "refactor: extract native rendering helpers"
```

---

### Task 4: Extract Web Mercator Helpers

**Files:**
- Create: `app/src/main/cpp/lib/geo/WebMercator.hpp`
- Create: `app/src/main/cpp/lib/geo/WebMercator.cpp`
- Modify: `app/src/main/cpp/native-lib.cpp`

- [ ] **Step 1: Create projection declarations**

Create `WebMercator.hpp`:

```cpp
#pragma once

#include "custom_map_layers/maplibre/custom_layer_host.hpp"

namespace custom_map_layers::geo {

struct ScreenPoint {
    double x;
    double y;
};

double longitudeToMercatorX(double longitude);
double latitudeToMercatorY(double latitude);
double metersToMercatorUnits(double meters, double latitude);

ScreenPoint projectToNdc(
        double longitude,
        double latitude,
        double altitudeMeters,
        const mbgl::style::CustomLayerRenderParameters& parameters
);

} // namespace custom_map_layers::geo
```

- [ ] **Step 2: Move projection implementation**

Create `WebMercator.cpp` by moving the existing `longitudeToMercatorX`, `latitudeToMercatorY`, `metersToMercatorUnits`, and `projectToNdc` implementations from `native-lib.cpp`.

Keep the existing constants and math behavior:

```cpp
constexpr double kEarthCircumferenceMeters = 40075016.68557849;
constexpr double tileSize = 512.0;
```

- [ ] **Step 3: Compile geo extraction**

Run:

```bash
./gradlew :app:compileDebugKotlin -q
```

Expected: build succeeds and exclamation geometry still uses the extracted projection helpers.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/cpp/lib/geo app/src/main/cpp/native-lib.cpp
git commit -m "refactor: extract native map projection helpers"
```

---

### Task 5: Extract Temporary Exclamation Layer

**Files:**
- Create: `app/src/main/cpp/lib/layers/exclamation/ExclamationLayer.hpp`
- Create: `app/src/main/cpp/lib/layers/exclamation/ExclamationLayer.cpp`
- Modify: `app/src/main/cpp/native-lib.cpp`

- [ ] **Step 1: Create layer declaration**

Create `ExclamationLayer.hpp`:

```cpp
#pragma once

#include "custom_map_layers/maplibre/custom_layer_host.hpp"
#include "rendering/GlesProgram.hpp"
#include "rendering/VertexBuffer.hpp"

#include <GLES3/gl3.h>

#include <vector>

namespace custom_map_layers::layers::exclamation {

class ExclamationLayer final : public mbgl::style::CustomLayerHost {
public:
    void initialize() override;
    void render(const mbgl::style::CustomLayerRenderParameters& parameters) override;
    void contextLost() override;
    void deinitialize() override;

private:
    void resetState();

    rendering::GlesProgram program_;
    rendering::VertexBuffer vertexBuffer_;
    GLsizei vertexCount_ = 0;
    bool didLogFirstRender_ = false;
    std::vector<GLfloat> vertices_;
};

} // namespace custom_map_layers::layers::exclamation
```

- [ ] **Step 2: Move exclamation implementation**

Create `ExclamationLayer.cpp` by moving the current exclamation constants, shader sources, `appendTriangle`, `appendQuad`, `appendVerticalBillboardRect`, `buildExclamationMark`, and `ExclamationLayer` method bodies from `native-lib.cpp`.

Keep the proof-of-concept marker constants unchanged:

```cpp
constexpr const char* LOG_TAG = "NativeExclamationLayer";
constexpr double kCircleLatitude = 54.3744505;
constexpr double kCircleLongitude = 18.6502754;
constexpr double kStemHeightMeters = 160.0;
constexpr double kStemWidthMeters = 28.0;
constexpr double kDotSizeMeters = 44.0;
constexpr double kDotGapMeters = 24.0;
```

- [ ] **Step 3: Replace the monolith body with includes**

After moving layer logic, `native-lib.cpp` should contain only JNI includes and the JNI functions until Task 6 moves it:

```cpp
#include "layers/exclamation/ExclamationLayer.hpp"

#include <android/log.h>
#include <jni.h>

#include <memory>

namespace {
constexpr const char* LOG_TAG = "NativeExclamationLayer";
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_arhor_journey_feature_map_viewinterop_NativeExclamationLayer_nativeCreateContext(
        JNIEnv*,
        jclass
) {
    __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "nativeCreateContext");
    auto layer = std::make_unique<custom_map_layers::layers::exclamation::ExclamationLayer>();
    return reinterpret_cast<jlong>(layer.release());
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_arhor_journey_feature_map_viewinterop_NativeExclamationLayer_nativeDestroyContext(
        JNIEnv*,
        jclass,
        jlong context
) {
    __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "nativeDestroyContext");
    delete reinterpret_cast<custom_map_layers::layers::exclamation::ExclamationLayer*>(context);
}
```

- [ ] **Step 4: Compile layer extraction**

Run:

```bash
./gradlew :app:compileDebugKotlin -q
```

Expected: build succeeds and JNI still returns a `CustomLayerHost` pointer.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/cpp/lib/layers/exclamation app/src/main/cpp/native-lib.cpp
git commit -m "refactor: extract native exclamation layer"
```

---

### Task 6: Move JNI Source And Finalize CMake Layout

**Files:**
- Create: `app/src/main/cpp/lib/jni/custom_map_layers_jni.cpp`
- Modify: `app/src/main/cpp/CMakeLists.txt`
- Delete: `app/src/main/cpp/native-lib.cpp`
- Delete: `app/src/main/cpp/includes/`

- [ ] **Step 1: Move JNI implementation**

Move the remaining JNI-only content from `native-lib.cpp` into `lib/jni/custom_map_layers_jni.cpp`.

- [ ] **Step 2: Replace CMake source list**

Update `CMakeLists.txt` to the final target shape:

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

- [ ] **Step 3: Remove obsolete source paths**

Delete `app/src/main/cpp/native-lib.cpp`.

Delete `app/src/main/cpp/includes/` if it is still empty:

```bash
rmdir app/src/main/cpp/includes
```

- [ ] **Step 4: Compile final native layout**

Run:

```bash
./gradlew :app:compileDebugKotlin -q
```

Expected: build succeeds with all source files coming from `include/` and `lib/`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/cpp/CMakeLists.txt app/src/main/cpp/include app/src/main/cpp/lib
git add -u app/src/main/cpp
git commit -m "refactor: finalize custom map layers native layout"
```

---

### Task 7: Final Verification

**Files:**
- Test: native CMake configuration and Kotlin/JVM bridge behavior

- [ ] **Step 1: Run focused JVM test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeExclamationLayerTest"
```

Expected: test passes.

- [ ] **Step 2: Run native/Kotlin compile**

Run:

```bash
./gradlew :app:compileDebugKotlin -q
```

Expected: build succeeds.

- [ ] **Step 3: Optional runtime smoke check**

Launch the app on an Android device or emulator, open the map, and verify:

- the temporary red exclamation mark is visible
- map pan, zoom, rotate, and pitch still work
- logcat does not show `NativeExclamationLayer` shader, render, or GLES errors

- [ ] **Step 4: Record verification**

In the final implementation response, report the exact Gradle commands run and whether the manual runtime smoke check was performed.
