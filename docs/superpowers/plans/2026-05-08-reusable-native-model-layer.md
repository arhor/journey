# Reusable Native Model Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let Kotlin choose which native GLB models render on the map, where they render, and how they are scaled and rotated.

**Architecture:** Kotlin passes a fixed list of immutable model specs when attaching the single MapLibre custom layer. JNI converts those specs into native instance data. The native layer loads each unique asset path once per GL context, keeps one shared shader and vertex buffer, caches loaded model and texture resources by asset path, and renders each instance with its own lat/lon/altitude/scale/heading.

**Tech Stack:** Kotlin, JNI, Android `AssetManager`, MapLibre `CustomLayer`, C++17, OpenGL ES 3, cgltf, JUnit4, Kotest.

---

## File Structure

- Create `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeMapModelSpec.kt`
  - Kotlin-side immutable data for one native model instance.
- Modify `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayer.kt`
  - Accept model specs, pass them to native context creation, and keep context lifecycle behavior unchanged.
- Modify `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/MapLibreViewMapScreen.kt`
  - Pass the current tiger as a Kotlin spec.
- Create `app/src/main/cpp/lib/layers/model/ModelInstance.hpp`
  - Native data structure for one model instance and a helper for heading conversion.
- Modify `app/src/main/cpp/lib/jni/custom_map_layers_jni.cpp`
  - Convert `Array<NativeMapModelSpec>` into `std::vector<ModelInstance>`.
- Modify `app/src/main/cpp/lib/gltf/GltfModelLoader.hpp`
  - Replace `loadTiger` with `load(assetPath, logTag)`.
- Modify `app/src/main/cpp/lib/gltf/GltfModelLoader.cpp`
  - Read the model path supplied by Kotlin.
- Modify `app/src/main/cpp/lib/layers/model/ModelLayer.hpp`
  - Store model instances and per-asset cached resources.
- Modify `app/src/main/cpp/lib/layers/model/ModelLayer.cpp`
  - Remove hard-coded tiger configuration and render every provided instance.
- Modify `app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayerTest.kt`
  - Add source-boundary tests for Kotlin spec plumbing and native generalization.

## Task 1: Add Kotlin Model Specs And Bridge Tests

**Files:**
- Create: `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeMapModelSpec.kt`
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayer.kt`
- Test: `app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayerTest.kt`

- [ ] **Step 1: Add failing tests for Kotlin-side spec plumbing**

In `app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayerTest.kt`, add these tests after
`layerId should return native model layer id`:

```kotlin
    @Test
    fun `native map model spec should expose asset path location scale and heading`() {
        // Given
        val spec = NativeMapModelSpec(
            assetPath = "models/animal-tiger.glb",
            latitude = 54.3738000,
            longitude = 18.6508750,
            altitudeMeters = 3.0,
            scaleMetersPerModelUnit = 45.0,
            headingDegrees = 90.0,
        )

        // When

        // Then
        spec.assetPath shouldBe "models/animal-tiger.glb"
        spec.latitude shouldBe 54.3738000
        spec.longitude shouldBe 18.6508750
        spec.altitudeMeters shouldBe 3.0
        spec.scaleMetersPerModelUnit shouldBe 45.0
        spec.headingDegrees shouldBe 90.0
    }

    @Test
    fun `addToWithManagedContext should pass model specs into context creation`() {
        // Given
        val model = NativeMapModelSpec(
            assetPath = "models/animal-tiger.glb",
            latitude = 54.3738000,
            longitude = 18.6508750,
            altitudeMeters = 0.0,
            scaleMetersPerModelUnit = 45.0,
            headingDegrees = 0.0,
        )
        val createdContext = 42L
        val createdModels = mutableListOf<List<NativeMapModelSpec>>()
        val addedContexts = mutableListOf<Long>()
        var repaintCalls = 0

        // When
        NativeModelLayer.addToWithManagedContext(
            models = listOf(model),
            createContext = { models ->
                createdModels += models
                createdContext
            },
            destroyContext = {},
            addLayer = { context -> addedContexts += context },
            repaint = { repaintCalls += 1 },
        )

        // Then
        createdModels shouldBe listOf(listOf(model))
        addedContexts shouldBe listOf(createdContext)
        repaintCalls shouldBe 1
    }
```

Update the two existing `addToWithManagedContext` tests so they pass `models = emptyList()` and use the new
`createContext = { createdContext }` lambda shape:

```kotlin
        NativeModelLayer.addToWithManagedContext(
            models = emptyList(),
            createContext = { createdContext },
            destroyContext = { context -> destroyedContexts += context },
            addLayer = { context -> addedContexts += context },
            repaint = { repaintCalls += 1 },
        )
```

```kotlin
            NativeModelLayer.addToWithManagedContext(
                models = emptyList(),
                createContext = { createdContext },
                destroyContext = { context -> destroyedContexts += context },
                addLayer = { throw expected },
                repaint = { repaintCalls += 1 },
            )
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
```

Expected: FAIL because `NativeMapModelSpec` does not exist and `addToWithManagedContext` does not accept `models`.

- [ ] **Step 3: Create the Kotlin spec type**

Create `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeMapModelSpec.kt`:

```kotlin
package com.github.arhor.journey.feature.map.viewinterop

import androidx.compose.runtime.Immutable

@Immutable
internal data class NativeMapModelSpec(
    val assetPath: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 0.0,
    val scaleMetersPerModelUnit: Double,
    val headingDegrees: Double = 0.0,
)
```

- [ ] **Step 4: Thread specs through the Kotlin bridge**

In `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayer.kt`, change `addTo` and
`addToWithManagedContext` to:

```kotlin
    fun addTo(
        map: MapLibreMap,
        style: Style,
        assetManager: AssetManager,
        models: List<NativeMapModelSpec>,
    ) {
        addToWithManagedContext(
            models = models,
            createContext = { specs -> createContext(assetManager, specs) },
            destroyContext = ::destroyContext,
            addLayer = { context ->
                val customLayer = CustomLayer(LAYER_ID, context)
                style.addLayer(customLayer)
            },
            repaint = map::triggerRepaint,
        )
    }

    internal fun addToWithManagedContext(
        models: List<NativeMapModelSpec>,
        createContext: (List<NativeMapModelSpec>) -> Long,
        destroyContext: (Long) -> Unit,
        addLayer: (Long) -> Unit,
        repaint: () -> Unit,
    ) {
        val context = createContext(models)
        var layerAdded = false

        try {
            addLayer(context)
            layerAdded = true
            repaint()
        } catch (throwable: Throwable) {
            if (!layerAdded) {
                destroyContext(context)
            }
            throw throwable
        }
    }

    private fun createContext(
        assetManager: AssetManager,
        models: List<NativeMapModelSpec>,
    ): Long {
        nativeLibraryLoadedGate
        return createContextNative(assetManager, models.toTypedArray())
    }
```

Change the native declaration at the bottom of the file to:

```kotlin
    @JvmStatic
    private external fun createContextNative(
        assetManager: AssetManager,
        models: Array<NativeMapModelSpec>,
    ): Long
```

- [ ] **Step 5: Run the focused Kotlin test and verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
```

Expected: PASS for Kotlin compilation and bridge tests. Native compilation may still fail later until JNI is updated;
if this command reaches native symbol linkage checks and fails on the JNI signature, continue to Task 2.

- [ ] **Step 6: Commit Kotlin spec bridge**

Run:

```bash
git add app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeMapModelSpec.kt app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayer.kt app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayerTest.kt
git commit -m "Add native map model specs"
```

## Task 2: Pass The Current Tiger From Kotlin

**Files:**
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/MapLibreViewMapScreen.kt`
- Test: `app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayerTest.kt`

- [ ] **Step 1: Add a source-boundary test for the Kotlin tiger spec**

In `NativeModelLayerTest`, add this test before the native source tests:

```kotlin
    @Test
    fun `map screen should pass current tiger model spec from kotlin`() {
        // Given
        val source = File("src/main/java/com/github/arhor/journey/feature/map/viewinterop/MapLibreViewMapScreen.kt")
            .readText()

        // When
        val modelLayerCall = source
            .substringAfter("NativeModelLayer.addTo(")
            .substringBefore("viewportReporter.attach(map)")

        // Then
        modelLayerCall shouldContain "models = listOf("
        modelLayerCall shouldContain "NativeMapModelSpec("
        modelLayerCall shouldContain "assetPath = \"models/animal-tiger.glb\""
        modelLayerCall shouldContain "latitude = 54.3738000"
        modelLayerCall shouldContain "longitude = 18.6508750"
        modelLayerCall shouldContain "altitudeMeters = 0.0"
        modelLayerCall shouldContain "scaleMetersPerModelUnit = 45.0"
        modelLayerCall shouldContain "headingDegrees = 0.0"
    }
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
```

Expected: FAIL because `MapLibreViewMapScreen.kt` still calls `NativeModelLayer.addTo` without `models`.

- [ ] **Step 3: Pass the tiger spec from the map screen**

In `MapLibreViewMapScreen.kt`, update the `NativeModelLayer.addTo` call to:

```kotlin
            NativeModelLayer.addTo(
                map = map,
                style = style,
                assetManager = context.assets,
                models = listOf(
                    NativeMapModelSpec(
                        assetPath = "models/animal-tiger.glb",
                        latitude = 54.3738000,
                        longitude = 18.6508750,
                        altitudeMeters = 0.0,
                        scaleMetersPerModelUnit = 45.0,
                        headingDegrees = 0.0,
                    ),
                ),
            )
```

- [ ] **Step 4: Run the focused test and verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
```

Expected: PASS for the Kotlin source-boundary test. Native symbol work still happens in Task 3.

- [ ] **Step 5: Commit Kotlin map usage**

Run:

```bash
git add app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/MapLibreViewMapScreen.kt app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayerTest.kt
git commit -m "Pass tiger model spec from map screen"
```

## Task 3: Convert Kotlin Specs In JNI

**Files:**
- Create: `app/src/main/cpp/lib/layers/model/ModelInstance.hpp`
- Modify: `app/src/main/cpp/lib/jni/custom_map_layers_jni.cpp`
- Modify: `app/src/main/cpp/lib/layers/model/ModelLayer.hpp`
- Modify: `app/src/main/cpp/lib/layers/model/ModelLayer.cpp`
- Test: `app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayerTest.kt`

- [ ] **Step 1: Add failing native boundary tests for JNI conversion and constructor shape**

In `NativeModelLayerTest`, add these tests before
`native model renderer should use MapLibre projection matrix instead of manual camera rotation`:

```kotlin
    @Test
    fun `native model layer context creation should receive kotlin model spec array`() {
        // Given
        val kotlinSource = File("src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayer.kt")
            .readText()
        val jniSource = File("src/main/cpp/lib/jni/custom_map_layers_jni.cpp").readText()

        // When

        // Then
        kotlinSource shouldContain "models: Array<NativeMapModelSpec>"
        jniSource shouldContain "jobjectArray models"
        jniSource shouldContain "readModelInstances"
        jniSource shouldContain "getAssetPath"
        jniSource shouldContain "getLatitude"
        jniSource shouldContain "getLongitude"
        jniSource shouldContain "getAltitudeMeters"
        jniSource shouldContain "getScaleMetersPerModelUnit"
        jniSource shouldContain "getHeadingDegrees"
    }

    @Test
    fun `native model layer should be constructed with model instances`() {
        // Given
        val header = File("src/main/cpp/lib/layers/model/ModelLayer.hpp").readText()
        val source = File("src/main/cpp/lib/layers/model/ModelLayer.cpp").readText()

        // When

        // Then
        header shouldContain "std::vector<ModelInstance>"
        header shouldContain "ModelLayer(AAssetManager* assetManager, std::vector<ModelInstance> instances)"
        source shouldContain "ModelLayer::ModelLayer(AAssetManager* assetManager, std::vector<ModelInstance> instances)"
        source shouldContain "instances_(std::move(instances))"
    }
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
```

Expected: FAIL because JNI still receives only `AssetManager` and `ModelLayer` still accepts only `AAssetManager*`.

- [ ] **Step 3: Add native model instance data**

Create `app/src/main/cpp/lib/layers/model/ModelInstance.hpp`:

```cpp
#pragma once

#include <numbers>
#include <string>

namespace custom_map_layers::layers::model {

struct ModelInstance {
    std::string assetPath;
    double latitude = 0.0;
    double longitude = 0.0;
    double altitudeMeters = 0.0;
    double scaleMetersPerModelUnit = 1.0;
    double headingRadians = 0.0;
};

inline double degreesToRadians(double degrees) {
    return degrees * std::numbers::pi / 180.0;
}

}  // namespace custom_map_layers::layers::model
```

- [ ] **Step 4: Change `ModelLayer` constructor and fields**

In `ModelLayer.hpp`, add includes:

```cpp
#include <memory>
#include <string>
#include <unordered_map>
```

Add:

```cpp
#include "layers/model/ModelInstance.hpp"
```

Replace the constructor declaration:

```cpp
    ModelLayer(AAssetManager* assetManager, std::vector<ModelInstance> instances);
```

Add this private resource type before fields:

```cpp
    struct CachedModelResource {
        gltf::LoadedModel model;
        std::unique_ptr<rendering::GlTexture> texture;
        GLsizei vertexCount = 0;
    };
```

Replace these fields:

```cpp
    rendering::GlTexture texture_;
    gltf::LoadedModel model_;
```

with:

```cpp
    std::vector<ModelInstance> instances_;
    std::unordered_map<std::string, CachedModelResource> resourcesByAssetPath_;
```

Keep the shared `program_`, shared `vertexBuffer_`, `vertexCount_`, and state booleans.

- [ ] **Step 5: Convert Java model specs in JNI**

Replace `custom_map_layers_jni.cpp` with this implementation:

```cpp
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <jni.h>

#include <memory>
#include <string>
#include <vector>

#include "layers/model/ModelInstance.hpp"
#include "layers/model/ModelLayer.hpp"

namespace {

constexpr const char* log_tag = "NativeModelLayer";

std::string readString(JNIEnv* env, jobject owner, jmethodID method) {
    auto value = static_cast<jstring>(env->CallObjectMethod(owner, method));
    if (value == nullptr) {
        return {};
    }

    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars != nullptr ? chars : "";
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(value, chars);
    }
    env->DeleteLocalRef(value);
    return result;
}

std::vector<custom_map_layers::layers::model::ModelInstance> readModelInstances(JNIEnv* env, jobjectArray models) {
    std::vector<custom_map_layers::layers::model::ModelInstance> instances;
    if (models == nullptr) {
        return instances;
    }

    const jsize count = env->GetArrayLength(models);
    instances.reserve(static_cast<size_t>(count));

    jclass modelClass = nullptr;
    jmethodID getAssetPath = nullptr;
    jmethodID getLatitude = nullptr;
    jmethodID getLongitude = nullptr;
    jmethodID getAltitudeMeters = nullptr;
    jmethodID getScaleMetersPerModelUnit = nullptr;
    jmethodID getHeadingDegrees = nullptr;

    for (jsize index = 0; index < count; ++index) {
        jobject model = env->GetObjectArrayElement(models, index);
        if (model == nullptr) {
            continue;
        }

        if (modelClass == nullptr) {
            modelClass = env->GetObjectClass(model);
            getAssetPath = env->GetMethodID(modelClass, "getAssetPath", "()Ljava/lang/String;");
            getLatitude = env->GetMethodID(modelClass, "getLatitude", "()D");
            getLongitude = env->GetMethodID(modelClass, "getLongitude", "()D");
            getAltitudeMeters = env->GetMethodID(modelClass, "getAltitudeMeters", "()D");
            getScaleMetersPerModelUnit = env->GetMethodID(modelClass, "getScaleMetersPerModelUnit", "()D");
            getHeadingDegrees = env->GetMethodID(modelClass, "getHeadingDegrees", "()D");
        }

        const double headingDegrees = env->CallDoubleMethod(model, getHeadingDegrees);
        instances.push_back(
                custom_map_layers::layers::model::ModelInstance{
                        .assetPath = readString(env, model, getAssetPath),
                        .latitude = env->CallDoubleMethod(model, getLatitude),
                        .longitude = env->CallDoubleMethod(model, getLongitude),
                        .altitudeMeters = env->CallDoubleMethod(model, getAltitudeMeters),
                        .scaleMetersPerModelUnit = env->CallDoubleMethod(model, getScaleMetersPerModelUnit),
                        .headingRadians = custom_map_layers::layers::model::degreesToRadians(headingDegrees),
                }
        );
        env->DeleteLocalRef(model);
    }

    if (modelClass != nullptr) {
        env->DeleteLocalRef(modelClass);
    }
    return instances;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_arhor_journey_feature_map_viewinterop_NativeModelLayer_createContextNative(
        JNIEnv* env,
        jclass,
        jobject assetManager,
        jobjectArray models
) {
    __android_log_write(ANDROID_LOG_INFO, log_tag, "nativeCreateContext");
    AAssetManager* nativeAssetManager = AAssetManager_fromJava(env, assetManager);
    auto layer = std::make_unique<custom_map_layers::layers::model::ModelLayer>(
            nativeAssetManager,
            readModelInstances(env, models)
    );
    return reinterpret_cast<jlong>(layer.release());
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_arhor_journey_feature_map_viewinterop_NativeModelLayer_destroyContextNative(
        JNIEnv*,
        jclass,
        jlong context
) {
    __android_log_write(ANDROID_LOG_INFO, log_tag, "nativeDestroyContext");
    delete reinterpret_cast<custom_map_layers::layers::model::ModelLayer*>(context);
}
```

- [ ] **Step 6: Update the `ModelLayer` constructor implementation**

In `ModelLayer.cpp`, add:

```cpp
#include <memory>
```

Replace the constructor with:

```cpp
ModelLayer::ModelLayer(
        AAssetManager* assetManager,
        std::vector<ModelInstance> instances
) : assetManager_(assetManager), instances_(std::move(instances)) {}
```

- [ ] **Step 7: Run focused tests and Kotlin compilation**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
./gradlew :app:compileDebugKotlin -q
```

Expected: tests PASS or only fail on loader/render hard-coded constants addressed in Task 4. Kotlin compilation PASS.

- [ ] **Step 8: Commit JNI conversion**

Run:

```bash
git add app/src/main/cpp/lib/layers/model/ModelInstance.hpp app/src/main/cpp/lib/jni/custom_map_layers_jni.cpp app/src/main/cpp/lib/layers/model/ModelLayer.hpp app/src/main/cpp/lib/layers/model/ModelLayer.cpp app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayerTest.kt
git commit -m "Pass model instances through JNI"
```

## Task 4: Generalize GLB Loading And Native Rendering

**Files:**
- Modify: `app/src/main/cpp/lib/gltf/GltfModelLoader.hpp`
- Modify: `app/src/main/cpp/lib/gltf/GltfModelLoader.cpp`
- Modify: `app/src/main/cpp/lib/layers/model/ModelLayer.hpp`
- Modify: `app/src/main/cpp/lib/layers/model/ModelLayer.cpp`
- Test: `app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayerTest.kt`

- [ ] **Step 1: Add failing tests for path-based loading and no hard-coded transform constants**

In `NativeModelLayerTest`, add:

```kotlin
    @Test
    fun `native gltf loader should load caller supplied model asset path`() {
        // Given
        val header = File("src/main/cpp/lib/gltf/GltfModelLoader.hpp").readText()
        val source = File("src/main/cpp/lib/gltf/GltfModelLoader.cpp").readText()

        // When

        // Then
        header shouldContain "load(const std::string& assetPath, const char* logTag)"
        header shouldNotContain "loadTiger"
        source shouldContain "reader.readBytes(assetPath, logTag)"
        source shouldNotContain "tiger_model_path"
        source shouldNotContain "loadTiger"
    }

    @Test
    fun `native model renderer should use per instance location scale and heading`() {
        // Given
        val source = File("src/main/cpp/lib/layers/model/ModelLayer.cpp").readText()

        // When

        // Then
        source shouldContain "instance.latitude"
        source shouldContain "instance.longitude"
        source shouldContain "instance.altitudeMeters"
        source shouldContain "instance.scaleMetersPerModelUnit"
        source shouldContain "instance.headingRadians"
        source shouldNotContain token("marker", "_", "latitude")
        source shouldNotContain token("marker", "_", "longitude")
        source shouldNotContain token("marker", "_", "altitude", "_", "meters")
        source shouldNotContain token("model", "_", "meters", "_", "per", "_", "unit")
        source shouldNotContain token("model", "_", "heading", "_", "radians")
    }
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
```

Expected: FAIL because the loader still has `loadTiger` and `ModelLayer.cpp` still has hard-coded marker constants.

- [ ] **Step 3: Change `GltfModelLoader` to load a supplied asset path**

In `GltfModelLoader.hpp`, add:

```cpp
#include <string>
```

Replace the public load method with:

```cpp
    [[nodiscard]] std::optional<LoadedModel> load(const std::string& assetPath, const char* logTag) const;
```

In `GltfModelLoader.cpp`, delete:

```cpp
constexpr const char* tiger_model_path = "models/animal-tiger.glb";
```

Replace the method signature and asset read:

```cpp
std::optional<LoadedModel> GltfModelLoader::load(const std::string& assetPath, const char* logTag) const {
    const custom_map_layers::assets::AssetReader reader(assetManager_);
    const auto bytes = reader.readBytes(assetPath, logTag);
```

Leave the rest of the method body unchanged.

- [ ] **Step 4: Add helper signatures for cached resources and per-instance projection**

In `ModelLayer.hpp`, replace:

```cpp
    bool loadModelAndTexture();
    [[nodiscard]] std::vector<GLfloat> buildProjectedVertices(
            const mbgl::style::CustomLayerRenderParameters& params
    ) const;
```

with:

```cpp
    bool loadModelAndTexture(const std::string& assetPath);
    bool loadModelResources();
    [[nodiscard]] std::vector<GLfloat> buildProjectedVertices(
            const mbgl::style::CustomLayerRenderParameters& params,
            const ModelInstance& instance,
            const gltf::LoadedModel& model
    ) const;
```

- [ ] **Step 5: Remove hard-coded model constants and make rotation instance-based**

In `ModelLayer.cpp`, remove these constants:

```cpp
constexpr double marker_latitude = 54.3738000;
constexpr double marker_longitude = 18.6508750;
constexpr double marker_altitude_meters = 0.0;
constexpr double model_meters_per_unit = 45.0;
constexpr double model_heading_radians = 0.0;
```

Change `rotateLocalModelMeters` to:

```cpp
custom_map_layers::geo::LocalMeters rotateLocalModelMeters(
        const custom_map_layers::layers::model::ModelInstance& instance,
        const custom_map_layers::gltf::ModelVertex& vertex
) {
    const double modelEast = static_cast<double>(vertex.x) * instance.scaleMetersPerModelUnit;
    const double modelNorth = static_cast<double>(-vertex.z) * instance.scaleMetersPerModelUnit;
    const double modelUp = static_cast<double>(vertex.y) * instance.scaleMetersPerModelUnit;

    const double cosHeading = std::cos(instance.headingRadians);
    const double sinHeading = std::sin(instance.headingRadians);

    return custom_map_layers::geo::LocalMeters{
            .east = modelEast * cosHeading - modelNorth * sinHeading,
            .north = modelEast * sinHeading + modelNorth * cosHeading,
            .up = modelUp,
    };
}
```

- [ ] **Step 6: Load resources for unique instance assets**

Replace `loadModelAndTexture()` with:

```cpp
bool ModelLayer::loadModelAndTexture(const std::string& assetPath) {
    if (resourcesByAssetPath_.find(assetPath) != resourcesByAssetPath_.end()) {
        return true;
    }

    const custom_map_layers::gltf::GltfModelLoader loader(assetManager_);
    auto loadedModel = loader.load(assetPath, log_tag);
    if (!loadedModel.has_value()) {
        __android_log_print(ANDROID_LOG_ERROR, log_tag, "Missing model asset: %s", assetPath.c_str());
        return false;
    }

    const custom_map_layers::assets::AssetReader reader(assetManager_);
    const auto textureBytes = reader.readBytes(loadedModel->texturePath, log_tag);
    if (!textureBytes.has_value()) {
        __android_log_print(ANDROID_LOG_ERROR, log_tag, "Missing texture asset: %s", loadedModel->texturePath.c_str());
        return false;
    }

    const auto decoded = custom_map_layers::assets::decodePngRgba(*textureBytes, log_tag);
    if (!decoded.has_value()) {
        return false;
    }

    auto texture = std::make_unique<custom_map_layers::rendering::GlTexture>();
    if (!texture->createRgba(decoded->rgbaPixels.data(), decoded->width, decoded->height, log_tag)) {
        return false;
    }

    const GLsizei loadedVertexCount = static_cast<GLsizei>(loadedModel->triangleVertices.size());
    resourcesByAssetPath_.emplace(
            assetPath,
            CachedModelResource{
                    .model = std::move(*loadedModel),
                    .texture = std::move(texture),
                    .vertexCount = loadedVertexCount,
            }
    );
    __android_log_print(
            ANDROID_LOG_INFO,
            log_tag,
            "Loaded model asset=%s vertices=%d",
            assetPath.c_str(),
            loadedVertexCount
    );
    return true;
}

bool ModelLayer::loadModelResources() {
    bool hasLoadedModel = false;
    for (const ModelInstance& instance : instances_) {
        if (instance.assetPath.empty()) {
            __android_log_write(ANDROID_LOG_ERROR, log_tag, "Skipping model with empty asset path");
            continue;
        }
        hasLoadedModel = loadModelAndTexture(instance.assetPath) || hasLoadedModel;
    }
    loaded_ = hasLoadedModel;
    return hasLoadedModel;
}
```

In `initialize()`, replace:

```cpp
    if (!loadModelAndTexture()) {
        deinitialize();
    }
```

with:

```cpp
    if (!loadModelResources()) {
        deinitialize();
    }
```

- [ ] **Step 7: Render every model instance**

In `render`, replace the early texture check:

```cpp
    if (!loaded_ || program_.handle() == 0 || vertexBuffer_.handle() == 0 || texture_.handle() == 0) {
        return;
    }
```

with:

```cpp
    if (!loaded_ || program_.handle() == 0 || vertexBuffer_.handle() == 0) {
        return;
    }
```

Delete the old single-model projected-vertex block from `render`:

```cpp
    const std::vector<GLfloat> vertices = buildProjectedVertices(params);
    vertexCount_ = static_cast<GLsizei>(vertices.size() / 6);
    if (vertexCount_ == 0) {
        return;
    }

    if (shouldLogRender) {
        NdcBounds bounds;
        for (size_t vertexOffset = 0; vertexOffset < vertices.size(); vertexOffset += 6) {
            const auto w = static_cast<double>(vertices[vertexOffset + 3]);
            if (w == 0.0) {
                continue;
            }
            const double ndcX = static_cast<double>(vertices[vertexOffset]) / w;
            const double ndcY = static_cast<double>(vertices[vertexOffset + 1]) / w;
            bounds.minX = std::min(bounds.minX, ndcX);
            bounds.maxX = std::max(bounds.maxX, ndcX);
            bounds.minY = std::min(bounds.minY, ndcY);
            bounds.maxY = std::max(bounds.maxY, ndcY);
        }
        __android_log_print(
                ANDROID_LOG_INFO,
                log_tag,
                "model bounds ndc=(%.3f, %.3f)-(%.3f, %.3f)",
                bounds.minX,
                bounds.minY,
                bounds.maxX,
                bounds.maxY
        );
    }
```

Replace the single-model log message arguments with instance count:

```cpp
                "render %.0fx%.0f camera=(%.7f, %.7f) zoom=%.2f bearing=%.4f pitch=%.4f instances=%zu",
                params.width,
                params.height,
                params.latitude,
                params.longitude,
                params.zoom,
                params.bearing,
                params.pitch,
                instances_.size()
```

After `glUseProgram(program_.handle());`, replace the old texture bind and uniform setup:

```cpp
    texture_.bind(GL_TEXTURE0);
    const GLint textureUniform = glGetUniformLocation(program_.handle(), "u_texture");
    glUniform1i(textureUniform, 0);
```

with:

```cpp
    const GLint textureUniform = glGetUniformLocation(program_.handle(), "u_texture");
    glUniform1i(textureUniform, 0);
```

Move the existing model draw state setup so it appears before the per-instance loop:

```cpp
    glDisable(GL_STENCIL_TEST);
    glDisable(GL_CULL_FACE);
    glDisable(GL_BLEND);
    glDepthMask(GL_TRUE);
    glClearDepthf(1.0f);
    glClear(GL_DEPTH_BUFFER_BIT);
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LEQUAL);
```

Replace the old one-time vertex upload, attribute setup, and draw:

```cpp
    vertexBuffer_.upload(vertices);
    vertexBuffer_.bind();
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 4, GL_FLOAT, GL_FALSE, 6 * sizeof(GLfloat), nullptr);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(
            1,
            2,
            GL_FLOAT,
            GL_FALSE,
            6 * sizeof(GLfloat),
            reinterpret_cast<const void*>(4 * sizeof(GLfloat))
    );

    glDisable(GL_STENCIL_TEST);
    glDisable(GL_CULL_FACE);
    glDisable(GL_BLEND);
    glDepthMask(GL_TRUE);
    glClearDepthf(1.0f);
    glClear(GL_DEPTH_BUFFER_BIT);
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LEQUAL);
    glDrawArrays(GL_TRIANGLES, 0, vertexCount_);
```

with this loop:

```cpp
    vertexCount_ = 0;
    for (const ModelInstance& instance : instances_) {
        const auto resourceIterator = resourcesByAssetPath_.find(instance.assetPath);
        if (resourceIterator == resourcesByAssetPath_.end() || resourceIterator->second.texture == nullptr) {
            continue;
        }

        const CachedModelResource& resource = resourceIterator->second;
        if (resource.texture->handle() == 0) {
            continue;
        }

        resource.texture->bind(GL_TEXTURE0);
        const std::vector<GLfloat> vertices = buildProjectedVertices(params, instance, resource.model);
        const GLsizei instanceVertexCount = static_cast<GLsizei>(vertices.size() / 6);
        if (instanceVertexCount == 0) {
            continue;
        }

        vertexBuffer_.upload(vertices);
        vertexBuffer_.bind();
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 4, GL_FLOAT, GL_FALSE, 6 * sizeof(GLfloat), nullptr);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(
                1,
                2,
                GL_FLOAT,
                GL_FALSE,
                6 * sizeof(GLfloat),
                reinterpret_cast<const void*>(4 * sizeof(GLfloat))
        );
        glDrawArrays(GL_TRIANGLES, 0, instanceVertexCount);
        vertexCount_ += instanceVertexCount;
    }
```

Keep the existing GL state save and restore after the loop. The existing `glDisableVertexAttribArray(1)` and
`glDisableVertexAttribArray(0)` should remain after the loop, before state restoration.

- [ ] **Step 8: Project vertices from the instance data**

Change `buildProjectedVertices` to:

```cpp
std::vector<GLfloat> ModelLayer::buildProjectedVertices(
        const mbgl::style::CustomLayerRenderParameters& params,
        const ModelInstance& instance,
        const gltf::LoadedModel& model
) const {
    std::vector<GLfloat> vertices;
    vertices.reserve(model.triangleVertices.size() * 6);

    const double worldSize = 512.0 * std::pow(2.0, params.zoom);
    const double worldPixelsPerMeter = custom_map_layers::geo::metersToMercatorUnits(1.0, instance.latitude) * worldSize;
    const double originX = custom_map_layers::geo::longitudeToMercatorX(instance.longitude) * worldSize;
    const double originY = custom_map_layers::geo::latitudeToMercatorY(instance.latitude) * worldSize;

    for (const custom_map_layers::gltf::ModelVertex& vertex : model.triangleVertices) {
        const custom_map_layers::geo::LocalMeters localMeters = rotateLocalModelMeters(instance, vertex);
        const double worldX = originX + localMeters.east * worldPixelsPerMeter;
        const double worldY = originY - localMeters.north * worldPixelsPerMeter;
        const double altitudeMeters = instance.altitudeMeters + localMeters.up;
        const ClipPosition clipPosition = projectWorldToClip(params.projectionMatrix, worldX, worldY, altitudeMeters);

        vertices.push_back(static_cast<GLfloat>(clipPosition.x));
        vertices.push_back(static_cast<GLfloat>(clipPosition.y));
        vertices.push_back(static_cast<GLfloat>(clipPosition.z));
        vertices.push_back(static_cast<GLfloat>(clipPosition.w));
        vertices.push_back(vertex.u);
        vertices.push_back(vertex.v);
    }

    return vertices;
}
```

- [ ] **Step 9: Reset cached resources correctly**

In `contextLost`, replace:

```cpp
    texture_.forget();
```

with:

```cpp
    for (auto& entry : resourcesByAssetPath_) {
        if (entry.second.texture != nullptr) {
            entry.second.texture->forget();
        }
    }
    resourcesByAssetPath_.clear();
```

In `deinitialize`, replace:

```cpp
    texture_.reset();
```

with:

```cpp
    for (auto& entry : resourcesByAssetPath_) {
        if (entry.second.texture != nullptr) {
            entry.second.texture->reset();
        }
    }
    resourcesByAssetPath_.clear();
```

- [ ] **Step 10: Run focused tests and native compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
./gradlew :app:compileDebugKotlin -q
```

Expected: PASS. The native C++ source is compiled as part of Android build tasks; if the second command does not compile
native code in this project configuration, run `./gradlew :app:assembleDebug`.

- [ ] **Step 11: Commit generalized native rendering**

Run:

```bash
git add app/src/main/cpp/lib/gltf/GltfModelLoader.hpp app/src/main/cpp/lib/gltf/GltfModelLoader.cpp app/src/main/cpp/lib/layers/model/ModelLayer.hpp app/src/main/cpp/lib/layers/model/ModelLayer.cpp app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayerTest.kt
git commit -m "Render native models from kotlin specs"
```

## Task 5: Final Verification

**Files:**
- Verify only.

- [ ] **Step 1: Run all focused JVM tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
```

Expected: PASS.

- [ ] **Step 2: Compile app Kotlin**

Run:

```bash
./gradlew :app:compileDebugKotlin -q
```

Expected: PASS.

- [ ] **Step 3: Build debug APK if native C++ was not compiled by the previous command**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: PASS.

- [ ] **Step 4: Runtime smoke test on emulator or device**

Run:

```bash
./gradlew :app:installDebug
/Users/maksimburyshynets/Library/Android/sdk/platform-tools/adb shell monkey -p com.github.arhor.journey 1
/Users/maksimburyshynets/Library/Android/sdk/platform-tools/adb logcat -d -s NativeModelLayer Mbgl-MapRenderer
```

Expected:

- logcat includes `nativeCreateContext`;
- logcat includes `Loaded model asset=models/animal-tiger.glb`;
- logcat has no `NativeModelLayer` GLES, missing asset, or texture decode errors;
- the map still shows the tiger at the Kotlin-provided coordinate.

- [ ] **Step 5: Inspect final status**

Run:

```bash
git status --short
```

Expected: clean worktree after all task commits.
