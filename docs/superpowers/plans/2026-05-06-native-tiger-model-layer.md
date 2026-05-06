# Native Tiger Model Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the temporary native exclamation mark custom layer with a native MapLibre custom layer that loads and renders `models/animal-tiger.glb` at hard-coded coordinates.

**Architecture:** Kotlin owns MapLibre `CustomLayer` registration and passes Android `AssetManager` into JNI. Native code uses `cgltf` for GLB/glTF structure parsing, Android asset APIs for bundled files, `AImageDecoder` for PNG decode, and GLES helpers for shader, buffer, and texture ownership. The first renderer is a hard-coded tiger marker, but asset loading, CPU model data, and GLES texture/buffer helpers are separated for later model marker generalization.

**Tech Stack:** Kotlin, MapLibre Android `CustomLayer`, JNI, Android NDK `AAssetManager`, `AImageDecoder`, `cgltf` v1.15, OpenGL ES 3, CMake, JUnit4, Kotest.

---

## File Structure

Create:

- `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayer.kt`: Kotlin bridge for the model custom layer.
- `app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayerTest.kt`: JVM tests for the Kotlin bridge.
- `app/src/main/cpp/third_party/cgltf/cgltf.h`: Vendored `cgltf` v1.15 header.
- `app/src/main/cpp/lib/gltf/cgltf_impl.cpp`: Single `CGLTF_IMPLEMENTATION` translation unit.
- `app/src/main/cpp/lib/assets/AssetReader.hpp`
- `app/src/main/cpp/lib/assets/AssetReader.cpp`
- `app/src/main/cpp/lib/assets/ImageDecoder.hpp`
- `app/src/main/cpp/lib/assets/ImageDecoder.cpp`
- `app/src/main/cpp/lib/gltf/LoadedModel.hpp`
- `app/src/main/cpp/lib/gltf/GltfModelLoader.hpp`
- `app/src/main/cpp/lib/gltf/GltfModelLoader.cpp`
- `app/src/main/cpp/lib/rendering/GlTexture.hpp`
- `app/src/main/cpp/lib/rendering/GlTexture.cpp`
- `app/src/main/cpp/lib/layers/model/ModelLayer.hpp`
- `app/src/main/cpp/lib/layers/model/ModelLayer.cpp`

Modify:

- `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/MapLibreViewMapScreen.kt`: add the model layer instead of the exclamation layer.
- `app/src/main/cpp/lib/jni/custom_map_layers_jni.cpp`: expose JNI functions for `NativeModelLayer`.
- `app/src/main/cpp/CMakeLists.txt`: include new native files, `cgltf` include path, and `jnigraphics`.

Delete after replacement:

- `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeExclamationLayer.kt`
- `app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeExclamationLayerTest.kt`
- `app/src/main/cpp/lib/layers/exclamation/ExclamationLayer.cpp`
- `app/src/main/cpp/lib/layers/exclamation/ExclamationLayer.hpp`

---

### Task 1: Add The Kotlin Model Layer Bridge With Failing Tests

**Files:**
- Create: `app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayerTest.kt`
- Create: `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayer.kt`

- [ ] **Step 1: Write the failing bridge tests**

Create `app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayerTest.kt`:

```kotlin
package com.github.arhor.journey.feature.map.viewinterop

import io.kotest.matchers.shouldBe
import org.junit.Test

class NativeModelLayerTest {

    @Test
    fun `layerId should return native model layer id`() {
        // Given

        // When
        val actual = NativeModelLayer.layerId()

        // Then
        actual shouldBe "native-model-layer"
    }

    @Test
    fun `addToWithManagedContext should repaint when layer is added`() {
        // Given
        val createdContext = 42L
        val addedContexts = mutableListOf<Long>()
        val destroyedContexts = mutableListOf<Long>()
        var repaintCalls = 0

        // When
        NativeModelLayer.addToWithManagedContext(
            createContext = { createdContext },
            destroyContext = { context -> destroyedContexts += context },
            addLayer = { context -> addedContexts += context },
            repaint = { repaintCalls += 1 },
        )

        // Then
        addedContexts shouldBe listOf(createdContext)
        destroyedContexts shouldBe emptyList()
        repaintCalls shouldBe 1
    }

    @Test
    fun `addToWithManagedContext should destroy context and rethrow when add layer throws after context creation`() {
        // Given
        val createdContext = 42L
        val expected = IllegalStateException("add-layer-failed")
        val destroyedContexts = mutableListOf<Long>()
        var repaintCalls = 0

        // When
        val actual = try {
            NativeModelLayer.addToWithManagedContext(
                createContext = { createdContext },
                destroyContext = { context -> destroyedContexts += context },
                addLayer = { throw expected },
                repaint = { repaintCalls += 1 },
            )
            null
        } catch (throwable: Throwable) {
            throwable
        }

        // Then
        actual shouldBe expected
        destroyedContexts shouldBe listOf(createdContext)
        repaintCalls shouldBe 0
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```shell
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
```

Expected: fail to compile because `NativeModelLayer` does not exist.

- [ ] **Step 3: Add `NativeModelLayer.kt`**

Create `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayer.kt`:

```kotlin
package com.github.arhor.journey.feature.map.viewinterop

import android.content.res.AssetManager
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CustomLayer

internal object NativeModelLayer {
    private val nativeLibraryLoadedGate by lazy {
        System.loadLibrary("custom-map-layers")
    }

    private const val LAYER_ID = "native-model-layer"

    fun addTo(
        map: MapLibreMap,
        style: Style,
        assetManager: AssetManager,
    ) {
        addToWithManagedContext(
            createContext = { createContext(assetManager) },
            destroyContext = ::destroyContext,
            addLayer = { context ->
                val customLayer = CustomLayer(LAYER_ID, context)
                style.addLayer(customLayer)
            },
            repaint = map::triggerRepaint,
        )
    }

    internal fun layerId(): String = LAYER_ID

    internal fun addToWithManagedContext(
        createContext: () -> Long,
        destroyContext: (Long) -> Unit,
        addLayer: (Long) -> Unit,
        repaint: () -> Unit,
    ) {
        val context = createContext()
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

    private fun createContext(assetManager: AssetManager): Long {
        nativeLibraryLoadedGate
        return createContextNative(assetManager)
    }

    private fun destroyContext(context: Long) {
        if (context == 0L) {
            return
        }
        nativeLibraryLoadedGate
        destroyContextNative(context)
    }

    @JvmStatic
    private external fun createContextNative(assetManager: AssetManager): Long

    @JvmStatic
    private external fun destroyContextNative(context: Long)
}
```

- [ ] **Step 4: Run the bridge tests to verify they pass**

Run:

```shell
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
```

Expected: pass.

- [ ] **Step 5: Commit**

```shell
git add app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayer.kt app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeModelLayerTest.kt
git commit -m "Add native model layer Kotlin bridge"
```

---

### Task 2: Wire The Kotlin Map Screen To The Model Layer

**Files:**
- Modify: `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/MapLibreViewMapScreen.kt`
- Delete: `app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeExclamationLayer.kt`
- Delete: `app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeExclamationLayerTest.kt`

- [ ] **Step 1: Replace the layer registration call**

In `MapLibreViewMapScreen.kt`, replace:

```kotlin
NativeExclamationLayer.addTo(map, style)
```

with:

```kotlin
NativeModelLayer.addTo(
    map = map,
    style = style,
    assetManager = context.assets,
)
```

- [ ] **Step 2: Delete the exclamation Kotlin bridge and test**

Delete:

```text
app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeExclamationLayer.kt
app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeExclamationLayerTest.kt
```

- [ ] **Step 3: Run the new bridge tests**

Run:

```shell
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
```

Expected: pass.

- [ ] **Step 4: Commit**

```shell
git add app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/MapLibreViewMapScreen.kt app/src/main/java/com/github/arhor/journey/feature/map/viewinterop/NativeExclamationLayer.kt app/src/test/java/com/github/arhor/journey/feature/map/viewinterop/NativeExclamationLayerTest.kt
git commit -m "Use native model layer on the map"
```

---

### Task 3: Vendor cgltf And Update Native Build Wiring

**Files:**
- Create: `app/src/main/cpp/third_party/cgltf/cgltf.h`
- Create: `app/src/main/cpp/lib/gltf/cgltf_impl.cpp`
- Modify: `app/src/main/cpp/CMakeLists.txt`

- [ ] **Step 1: Vendor `cgltf.h` v1.15**

Create the directory:

```shell
mkdir -p app/src/main/cpp/third_party/cgltf
```

Download the pinned header:

```shell
curl -L https://raw.githubusercontent.com/jkuhlmann/cgltf/v1.15/cgltf.h -o app/src/main/cpp/third_party/cgltf/cgltf.h
```

Expected: `app/src/main/cpp/third_party/cgltf/cgltf.h` exists and contains `#ifndef CGLTF_H_INCLUDED`.

- [ ] **Step 2: Add the implementation translation unit**

Create `app/src/main/cpp/lib/gltf/cgltf_impl.cpp`:

```cpp
#define CGLTF_IMPLEMENTATION
#include "cgltf.h"
```

- [ ] **Step 3: Update CMake sources, includes, and libraries**

Replace `app/src/main/cpp/CMakeLists.txt` with:

```cmake
cmake_minimum_required(VERSION 4.1.2)

project(custom-map-layers LANGUAGES CXX)

add_library(custom-map-layers SHARED)

target_sources(
        custom-map-layers
        PRIVATE
        lib/jni/custom_map_layers_jni.cpp
        lib/layers/model/ModelLayer.cpp
        lib/assets/AssetReader.cpp
        lib/assets/ImageDecoder.cpp
        lib/gltf/GltfModelLoader.cpp
        lib/gltf/cgltf_impl.cpp
        lib/geo/WebMercator.cpp
        lib/rendering/GlError.cpp
        lib/rendering/GlesProgram.cpp
        lib/rendering/GlTexture.cpp
        lib/rendering/VertexBuffer.cpp
)

target_compile_features(
        custom-map-layers
        PRIVATE
        cxx_std_20
)

target_include_directories(
        custom-map-layers
        PRIVATE
        include
        lib
        third_party/cgltf
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
        jnigraphics
        GLESv3
)
```

- [ ] **Step 4: Run compile to expose missing native files**

Run:

```shell
./gradlew :app:compileDebugKotlin -q
```

Expected: fail during native configure/compile because the new files listed in CMake are not created yet.

- [ ] **Step 5: Commit only vendoring and build wiring**

```shell
git add app/src/main/cpp/third_party/cgltf/cgltf.h app/src/main/cpp/lib/gltf/cgltf_impl.cpp app/src/main/cpp/CMakeLists.txt
git commit -m "Vendor cgltf for native model loading"
```

---

### Task 4: Add Native Asset, Image, Texture, And Model Data Helpers

**Files:**
- Create: `app/src/main/cpp/lib/assets/AssetReader.hpp`
- Create: `app/src/main/cpp/lib/assets/AssetReader.cpp`
- Create: `app/src/main/cpp/lib/assets/ImageDecoder.hpp`
- Create: `app/src/main/cpp/lib/assets/ImageDecoder.cpp`
- Create: `app/src/main/cpp/lib/rendering/GlTexture.hpp`
- Create: `app/src/main/cpp/lib/rendering/GlTexture.cpp`
- Create: `app/src/main/cpp/lib/gltf/LoadedModel.hpp`

- [ ] **Step 1: Add `AssetReader`**

Create `app/src/main/cpp/lib/assets/AssetReader.hpp`:

```cpp
#pragma once

#include <android/asset_manager.h>

#include <optional>
#include <string>
#include <vector>

namespace custom_map_layers::assets {

class AssetReader {
public:
    explicit AssetReader(AAssetManager* assetManager);

    [[nodiscard]] std::optional<std::vector<unsigned char>> readBytes(
            const std::string& path,
            const char* logTag
    ) const;

private:
    AAssetManager* assetManager_;
};

}  // namespace custom_map_layers::assets
```

Create `app/src/main/cpp/lib/assets/AssetReader.cpp`:

```cpp
#include "assets/AssetReader.hpp"

#include <android/log.h>

namespace custom_map_layers::assets {

AssetReader::AssetReader(AAssetManager* assetManager) : assetManager_(assetManager) {}

std::optional<std::vector<unsigned char>> AssetReader::readBytes(
        const std::string& path,
        const char* logTag
) const {
    if (assetManager_ == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, logTag, "AssetManager is null while reading %s", path.c_str());
        return std::nullopt;
    }

    AAsset* asset = AAssetManager_open(assetManager_, path.c_str(), AASSET_MODE_BUFFER);
    if (asset == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, logTag, "Missing asset: %s", path.c_str());
        return std::nullopt;
    }

    const off64_t length = AAsset_getLength64(asset);
    if (length <= 0) {
        __android_log_print(ANDROID_LOG_ERROR, logTag, "Asset has invalid length: %s", path.c_str());
        AAsset_close(asset);
        return std::nullopt;
    }

    std::vector<unsigned char> bytes(static_cast<size_t>(length));
    size_t offset = 0;
    while (offset < bytes.size()) {
        const int read = AAsset_read(asset, bytes.data() + offset, bytes.size() - offset);
        if (read < 0) {
            __android_log_print(ANDROID_LOG_ERROR, logTag, "Failed reading asset: %s", path.c_str());
            AAsset_close(asset);
            return std::nullopt;
        }
        if (read == 0) {
            break;
        }
        offset += static_cast<size_t>(read);
    }
    AAsset_close(asset);

    if (offset != bytes.size()) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                logTag,
                "Short asset read for %s: expected=%zu actual=%zu",
                path.c_str(),
                bytes.size(),
                offset
        );
        return std::nullopt;
    }

    return bytes;
}

}  // namespace custom_map_layers::assets
```

- [ ] **Step 2: Add `ImageDecoder`**

Create `app/src/main/cpp/lib/assets/ImageDecoder.hpp`:

```cpp
#pragma once

#include <optional>
#include <vector>

namespace custom_map_layers::assets {

struct DecodedImage {
    int width = 0;
    int height = 0;
    std::vector<unsigned char> rgbaPixels;
};

std::optional<DecodedImage> decodePngRgba(
        const std::vector<unsigned char>& pngBytes,
        const char* logTag
);

}  // namespace custom_map_layers::assets
```

Create `app/src/main/cpp/lib/assets/ImageDecoder.cpp`:

```cpp
#include "assets/ImageDecoder.hpp"

#include <android/imagedecoder.h>
#include <android/log.h>

namespace custom_map_layers::assets {

std::optional<DecodedImage> decodePngRgba(
        const std::vector<unsigned char>& pngBytes,
        const char* logTag
) {
    AImageDecoder* decoder = nullptr;
    int result = AImageDecoder_createFromBuffer(
            pngBytes.data(),
            pngBytes.size(),
            &decoder
    );
    if (result != ANDROID_IMAGE_DECODER_SUCCESS || decoder == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, logTag, "Failed to create image decoder: %d", result);
        return std::nullopt;
    }

    const AImageDecoderHeaderInfo* header = AImageDecoder_getHeaderInfo(decoder);
    const int width = AImageDecoderHeaderInfo_getWidth(header);
    const int height = AImageDecoderHeaderInfo_getHeight(header);
    AImageDecoder_setAndroidBitmapFormat(decoder, ANDROID_BITMAP_FORMAT_RGBA_8888);

    const size_t stride = static_cast<size_t>(width) * 4U;
    const size_t size = stride * static_cast<size_t>(height);
    std::vector<unsigned char> pixels(size);
    result = AImageDecoder_decodeImage(decoder, pixels.data(), stride, size);
    AImageDecoder_delete(decoder);

    if (result != ANDROID_IMAGE_DECODER_SUCCESS) {
        __android_log_print(ANDROID_LOG_ERROR, logTag, "Failed to decode image: %d", result);
        return std::nullopt;
    }

    return DecodedImage{
            .width = width,
            .height = height,
            .rgbaPixels = std::move(pixels),
    };
}

}  // namespace custom_map_layers::assets
```

- [ ] **Step 3: Add `GlTexture`**

Create `app/src/main/cpp/lib/rendering/GlTexture.hpp`:

```cpp
#pragma once

#include <GLES3/gl3.h>

namespace custom_map_layers::rendering {

class GlTexture {
public:
    GlTexture() = default;
    ~GlTexture() = default;

    GlTexture(const GlTexture&) = delete;
    GlTexture& operator=(const GlTexture&) = delete;

    bool createRgba(const unsigned char* pixels, int width, int height, const char* logTag);
    void bind(GLenum textureUnit) const;
    void reset();
    void forget();

    [[nodiscard]] GLuint handle() const;

private:
    GLuint texture_ = 0;
};

}  // namespace custom_map_layers::rendering
```

Create `app/src/main/cpp/lib/rendering/GlTexture.cpp`:

```cpp
#include "rendering/GlTexture.hpp"

#include "rendering/GlError.hpp"

namespace custom_map_layers::rendering {

bool GlTexture::createRgba(const unsigned char* pixels, int width, int height, const char* logTag) {
    reset();
    glGenTextures(1, &texture_);
    if (texture_ == 0) {
        logGlErrors("glGenTextures", logTag);
        return false;
    }

    glBindTexture(GL_TEXTURE_2D, texture_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexImage2D(
            GL_TEXTURE_2D,
            0,
            GL_RGBA,
            width,
            height,
            0,
            GL_RGBA,
            GL_UNSIGNED_BYTE,
            pixels
    );
    glGenerateMipmap(GL_TEXTURE_2D);
    glBindTexture(GL_TEXTURE_2D, 0);
    logGlErrors("createRgba", logTag);
    return texture_ != 0;
}

void GlTexture::bind(GLenum textureUnit) const {
    glActiveTexture(textureUnit);
    glBindTexture(GL_TEXTURE_2D, texture_);
}

void GlTexture::reset() {
    if (texture_ != 0) {
        glDeleteTextures(1, &texture_);
        texture_ = 0;
    }
}

void GlTexture::forget() {
    texture_ = 0;
}

GLuint GlTexture::handle() const {
    return texture_;
}

}  // namespace custom_map_layers::rendering
```

- [ ] **Step 4: Add `LoadedModel` CPU structs**

Create `app/src/main/cpp/lib/gltf/LoadedModel.hpp`:

```cpp
#pragma once

#include <string>
#include <vector>

namespace custom_map_layers::gltf {

struct ModelVertex {
    float x = 0.0f;
    float y = 0.0f;
    float z = 0.0f;
    float u = 0.0f;
    float v = 0.0f;
};

struct LoadedModel {
    std::vector<ModelVertex> triangleVertices;
    std::string texturePath;
};

}  // namespace custom_map_layers::gltf
```

- [ ] **Step 5: Run compile to verify helper syntax**

Run:

```shell
./gradlew :app:compileDebugKotlin -q
```

Expected: fail because `GltfModelLoader`, `ModelLayer`, and JNI references are not complete yet. There should be no errors in the helper files added in this task.

- [ ] **Step 6: Commit**

```shell
git add app/src/main/cpp/lib/assets app/src/main/cpp/lib/rendering/GlTexture.hpp app/src/main/cpp/lib/rendering/GlTexture.cpp app/src/main/cpp/lib/gltf/LoadedModel.hpp
git commit -m "Add native asset and texture helpers"
```

---

### Task 5: Add The cgltf Model Loader

**Files:**
- Create: `app/src/main/cpp/lib/gltf/GltfModelLoader.hpp`
- Create: `app/src/main/cpp/lib/gltf/GltfModelLoader.cpp`

- [ ] **Step 1: Add loader interface**

Create `app/src/main/cpp/lib/gltf/GltfModelLoader.hpp`:

```cpp
#pragma once

#include <android/asset_manager.h>

#include <optional>
#include <string>

#include "gltf/LoadedModel.hpp"

namespace custom_map_layers::gltf {

class GltfModelLoader {
public:
    explicit GltfModelLoader(AAssetManager* assetManager);

    [[nodiscard]] std::optional<LoadedModel> loadTiger(const char* logTag) const;

private:
    AAssetManager* assetManager_;
};

}  // namespace custom_map_layers::gltf
```

- [ ] **Step 2: Add loader implementation**

Create `app/src/main/cpp/lib/gltf/GltfModelLoader.cpp`:

```cpp
#include "gltf/GltfModelLoader.hpp"

#include <android/log.h>

#include <array>
#include <cmath>
#include <cstring>

#include "assets/AssetReader.hpp"
#include "cgltf.h"

namespace {

constexpr const char* kTigerModelPath = "models/animal-tiger.glb";
constexpr const char* kTextureFallbackPath = "models/textures/colormap.png";

using custom_map_layers::gltf::LoadedModel;
using custom_map_layers::gltf::ModelVertex;

std::string resolveTexturePath(const char* uri) {
    if (uri == nullptr || std::strlen(uri) == 0) {
        return kTextureFallbackPath;
    }
    if (std::strcmp(uri, "Textures/colormap.png") == 0) {
        return kTextureFallbackPath;
    }
    return std::string("models/") + uri;
}

void transformPosition(const cgltf_float matrix[16], const float in[3], float out[3]) {
    out[0] = matrix[0] * in[0] + matrix[4] * in[1] + matrix[8] * in[2] + matrix[12];
    out[1] = matrix[1] * in[0] + matrix[5] * in[1] + matrix[9] * in[2] + matrix[13];
    out[2] = matrix[2] * in[0] + matrix[6] * in[1] + matrix[10] * in[2] + matrix[14];
}

void appendPrimitive(
        const cgltf_primitive& primitive,
        const cgltf_float matrix[16],
        std::vector<ModelVertex>& vertices,
        const char* logTag
) {
    const cgltf_accessor* positionAccessor = nullptr;
    const cgltf_accessor* texcoordAccessor = nullptr;

    for (cgltf_size attributeIndex = 0; attributeIndex < primitive.attributes_count; ++attributeIndex) {
        const cgltf_attribute& attribute = primitive.attributes[attributeIndex];
        if (attribute.type == cgltf_attribute_type_position) {
            positionAccessor = attribute.data;
        } else if (attribute.type == cgltf_attribute_type_texcoord && attribute.index == 0) {
            texcoordAccessor = attribute.data;
        }
    }

    if (positionAccessor == nullptr || texcoordAccessor == nullptr || primitive.indices == nullptr) {
        __android_log_write(ANDROID_LOG_ERROR, logTag, "Primitive missing POSITION, TEXCOORD_0, or indices");
        return;
    }

    const cgltf_size indexCount = primitive.indices->count;
    vertices.reserve(vertices.size() + static_cast<size_t>(indexCount));

    for (cgltf_size i = 0; i < indexCount; ++i) {
        const cgltf_size vertexIndex = cgltf_accessor_read_index(primitive.indices, i);

        float position[3] = {0.0f, 0.0f, 0.0f};
        float texcoord[2] = {0.0f, 0.0f};
        float transformed[3] = {0.0f, 0.0f, 0.0f};
        cgltf_accessor_read_float(positionAccessor, vertexIndex, position, 3);
        cgltf_accessor_read_float(texcoordAccessor, vertexIndex, texcoord, 2);
        transformPosition(matrix, position, transformed);

        vertices.push_back(ModelVertex{
                .x = transformed[0],
                .y = transformed[1],
                .z = transformed[2],
                .u = texcoord[0],
                .v = 1.0f - texcoord[1],
        });
    }
}

void appendNode(
        const cgltf_node& node,
        std::vector<ModelVertex>& vertices,
        std::string& texturePath,
        const char* logTag
) {
    cgltf_float matrix[16];
    cgltf_node_transform_world(&node, matrix);

    if (node.mesh != nullptr) {
        for (cgltf_size primitiveIndex = 0; primitiveIndex < node.mesh->primitives_count; ++primitiveIndex) {
            const cgltf_primitive& primitive = node.mesh->primitives[primitiveIndex];
            if (primitive.type != cgltf_primitive_type_triangles) {
                __android_log_write(ANDROID_LOG_WARN, logTag, "Skipping non-triangle primitive");
                continue;
            }
            appendPrimitive(primitive, matrix, vertices, logTag);

            if (texturePath.empty() && primitive.material != nullptr) {
                const cgltf_texture_view& baseColor =
                        primitive.material->pbr_metallic_roughness.base_color_texture;
                if (baseColor.texture != nullptr && baseColor.texture->image != nullptr) {
                    texturePath = resolveTexturePath(baseColor.texture->image->uri);
                }
            }
        }
    }

    for (cgltf_size childIndex = 0; childIndex < node.children_count; ++childIndex) {
        appendNode(*node.children[childIndex], vertices, texturePath, logTag);
    }
}

}  // namespace

namespace custom_map_layers::gltf {

GltfModelLoader::GltfModelLoader(AAssetManager* assetManager) : assetManager_(assetManager) {}

std::optional<LoadedModel> GltfModelLoader::loadTiger(const char* logTag) const {
    const custom_map_layers::assets::AssetReader reader(assetManager_);
    const auto bytes = reader.readBytes(kTigerModelPath, logTag);
    if (!bytes.has_value()) {
        return std::nullopt;
    }

    cgltf_options options = {};
    cgltf_data* data = nullptr;
    const cgltf_result parseResult = cgltf_parse(
            &options,
            bytes->data(),
            bytes->size(),
            &data
    );
    if (parseResult != cgltf_result_success || data == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, logTag, "cgltf_parse failed: %d", parseResult);
        return std::nullopt;
    }

    const cgltf_result bufferResult = cgltf_load_buffers(
            &options,
            data,
            kTigerModelPath
    );
    if (bufferResult != cgltf_result_success) {
        __android_log_print(ANDROID_LOG_ERROR, logTag, "cgltf_load_buffers failed: %d", bufferResult);
        cgltf_free(data);
        return std::nullopt;
    }

    std::vector<ModelVertex> vertices;
    std::string texturePath;
    const cgltf_scene* scene = data->scene != nullptr ? data->scene : (data->scenes_count > 0 ? &data->scenes[0] : nullptr);
    if (scene == nullptr) {
        __android_log_write(ANDROID_LOG_ERROR, logTag, "GLB has no scene");
        cgltf_free(data);
        return std::nullopt;
    }

    for (cgltf_size nodeIndex = 0; nodeIndex < scene->nodes_count; ++nodeIndex) {
        appendNode(*scene->nodes[nodeIndex], vertices, texturePath, logTag);
    }

    cgltf_free(data);

    if (vertices.empty()) {
        __android_log_write(ANDROID_LOG_ERROR, logTag, "GLB produced no renderable vertices");
        return std::nullopt;
    }
    if (texturePath.empty()) {
        texturePath = kTextureFallbackPath;
    }

    return LoadedModel{
            .triangleVertices = std::move(vertices),
            .texturePath = std::move(texturePath),
    };
}

}  // namespace custom_map_layers::gltf
```

- [ ] **Step 3: Run compile to verify loader syntax**

Run:

```shell
./gradlew :app:compileDebugKotlin -q
```

Expected: fail because `ModelLayer` and JNI are not complete yet. There should be no `GltfModelLoader` compile errors.

- [ ] **Step 4: Commit**

```shell
git add app/src/main/cpp/lib/gltf/GltfModelLoader.hpp app/src/main/cpp/lib/gltf/GltfModelLoader.cpp
git commit -m "Load tiger GLB with cgltf"
```

---

### Task 6: Add The Native Model Layer And JNI Bridge

**Files:**
- Create: `app/src/main/cpp/lib/layers/model/ModelLayer.hpp`
- Create: `app/src/main/cpp/lib/layers/model/ModelLayer.cpp`
- Modify: `app/src/main/cpp/lib/jni/custom_map_layers_jni.cpp`
- Delete: `app/src/main/cpp/lib/layers/exclamation/ExclamationLayer.cpp`
- Delete: `app/src/main/cpp/lib/layers/exclamation/ExclamationLayer.hpp`

- [ ] **Step 1: Add `ModelLayer.hpp`**

Create `app/src/main/cpp/lib/layers/model/ModelLayer.hpp`:

```cpp
#pragma once

#include <android/asset_manager.h>
#include <GLES3/gl3.h>

#include <vector>

#include "custom_map_layers/maplibre/custom_layer_host.hpp"
#include "gltf/LoadedModel.hpp"
#include "rendering/GlesProgram.hpp"
#include "rendering/GlTexture.hpp"
#include "rendering/VertexBuffer.hpp"

namespace custom_map_layers::layers::model {

class ModelLayer final : public mbgl::style::CustomLayerHost {
public:
    explicit ModelLayer(AAssetManager* assetManager);

    void initialize() override;
    void render(const mbgl::style::CustomLayerRenderParameters& params) override;
    void contextLost() override;
    void deinitialize() override;

private:
    void resetState();
    bool loadModelAndTexture();
    std::vector<GLfloat> buildProjectedVertices(const mbgl::style::CustomLayerRenderParameters& params) const;

    AAssetManager* assetManager_;
    rendering::GlesProgram program_;
    rendering::VertexBuffer vertexBuffer_;
    rendering::GlTexture texture_;
    gltf::LoadedModel model_;
    GLsizei vertexCount_ = 0;
    bool loaded_ = false;
    bool didLogFirstRender_ = false;
};

}  // namespace custom_map_layers::layers::model
```

- [ ] **Step 2: Add `ModelLayer.cpp`**

Create `app/src/main/cpp/lib/layers/model/ModelLayer.cpp`:

```cpp
#include "layers/model/ModelLayer.hpp"

#include <android/log.h>

#include <cmath>

#include "assets/AssetReader.hpp"
#include "assets/ImageDecoder.hpp"
#include "geo/WebMercator.hpp"
#include "gltf/GltfModelLoader.hpp"
#include "rendering/GlError.hpp"

namespace {

constexpr const char* LOG_TAG = "NativeModelLayer";
constexpr double kMarkerLatitude = 54.3744505;
constexpr double kMarkerLongitude = 18.6502754;
constexpr double kEarthRadiusMeters = 6378137.0;
constexpr double kModelScaleMeters = 95.0;

constexpr const char* kVertexShaderSource = R"(#version 300 es
layout(location = 0) in vec3 a_pos;
layout(location = 1) in vec2 a_uv;
out vec2 v_uv;

void main() {
    gl_Position = vec4(a_pos, 1.0);
    v_uv = a_uv;
}
)";

constexpr const char* kFragmentShaderSource = R"(#version 300 es
precision highp float;
in vec2 v_uv;
uniform sampler2D u_texture;
out highp vec4 fragColor;

void main() {
    fragColor = texture(u_texture, v_uv);
}
)";

struct GeoOffset {
    double longitude;
    double latitude;
};

GeoOffset offsetMeters(double longitude, double latitude, double eastMeters, double northMeters) {
    constexpr double radiansToDegrees = 180.0 / 3.14159265358979323846264338327950288;
    constexpr double degreesToRadians = 3.14159265358979323846264338327950288 / 180.0;
    const double dLat = northMeters / kEarthRadiusMeters;
    const double dLon = eastMeters / (kEarthRadiusMeters * std::cos(latitude * degreesToRadians));
    return GeoOffset{
            .longitude = longitude + dLon * radiansToDegrees,
            .latitude = latitude + dLat * radiansToDegrees,
    };
}

}  // namespace

namespace custom_map_layers::layers::model {

ModelLayer::ModelLayer(AAssetManager* assetManager) : assetManager_(assetManager) {}

void ModelLayer::initialize() {
    __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "initialize");
    deinitialize();

    if (!program_.create(kVertexShaderSource, kFragmentShaderSource, LOG_TAG)) {
        deinitialize();
        return;
    }

    if (!vertexBuffer_.create(LOG_TAG)) {
        deinitialize();
        return;
    }

    if (!loadModelAndTexture()) {
        deinitialize();
        return;
    }
}

void ModelLayer::render(const mbgl::style::CustomLayerRenderParameters& params) {
    if (!loaded_ || program_.handle() == 0 || vertexBuffer_.handle() == 0 || texture_.handle() == 0) {
        return;
    }

    if (!didLogFirstRender_) {
        __android_log_print(
                ANDROID_LOG_INFO,
                LOG_TAG,
                "render %.0fx%.0f camera=(%.7f, %.7f) zoom=%.2f bearing=%.4f pitch=%.4f marker=(%.7f, %.7f) vertices=%d",
                params.width,
                params.height,
                params.latitude,
                params.longitude,
                params.zoom,
                params.bearing,
                params.pitch,
                kMarkerLatitude,
                kMarkerLongitude,
                vertexCount_
        );
        didLogFirstRender_ = true;
    }

    const std::vector<GLfloat> vertices = buildProjectedVertices(params);
    vertexCount_ = static_cast<GLsizei>(vertices.size() / 5);
    if (vertexCount_ == 0) {
        return;
    }

    glUseProgram(program_.handle());
    texture_.bind(GL_TEXTURE0);
    const GLint textureUniform = glGetUniformLocation(program_.handle(), "u_texture");
    glUniform1i(textureUniform, 0);

    vertexBuffer_.upload(vertices);
    vertexBuffer_.bind();
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 5 * sizeof(GLfloat), nullptr);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(
            1,
            2,
            GL_FLOAT,
            GL_FALSE,
            5 * sizeof(GLfloat),
            reinterpret_cast<const void*>(3 * sizeof(GLfloat))
    );

    glDisable(GL_STENCIL_TEST);
    glDisable(GL_DEPTH_TEST);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glDrawArrays(GL_TRIANGLES, 0, vertexCount_);

    glDisableVertexAttribArray(1);
    glDisableVertexAttribArray(0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);
    glUseProgram(0);
    custom_map_layers::rendering::logGlErrors("render", LOG_TAG);
}

void ModelLayer::contextLost() {
    __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "contextLost");
    texture_.forget();
    vertexBuffer_.forget();
    program_.forget();
    resetState();
}

void ModelLayer::deinitialize() {
    texture_.reset();
    vertexBuffer_.reset();
    program_.reset();
    custom_map_layers::rendering::logGlErrors("deinitialize", LOG_TAG);
    resetState();
}

bool ModelLayer::loadModelAndTexture() {
    const custom_map_layers::gltf::GltfModelLoader loader(assetManager_);
    const auto loadedModel = loader.loadTiger(LOG_TAG);
    if (!loadedModel.has_value()) {
        return false;
    }

    const custom_map_layers::assets::AssetReader reader(assetManager_);
    const auto textureBytes = reader.readBytes(loadedModel->texturePath, LOG_TAG);
    if (!textureBytes.has_value()) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Missing texture asset: %s", loadedModel->texturePath.c_str());
        return false;
    }

    const auto decoded = custom_map_layers::assets::decodePngRgba(*textureBytes, LOG_TAG);
    if (!decoded.has_value()) {
        return false;
    }

    if (!texture_.createRgba(decoded->rgbaPixels.data(), decoded->width, decoded->height, LOG_TAG)) {
        return false;
    }

    model_ = *loadedModel;
    vertexCount_ = static_cast<GLsizei>(model_.triangleVertices.size());
    loaded_ = true;
    __android_log_print(
            ANDROID_LOG_INFO,
            LOG_TAG,
            "Loaded tiger model vertices=%zu texture=%s",
            model_.triangleVertices.size(),
            model_.texturePath.c_str()
    );
    return true;
}

std::vector<GLfloat> ModelLayer::buildProjectedVertices(
        const mbgl::style::CustomLayerRenderParameters& params
) const {
    std::vector<GLfloat> vertices;
    vertices.reserve(model_.triangleVertices.size() * 5);

    for (const custom_map_layers::gltf::ModelVertex& vertex : model_.triangleVertices) {
        const double eastMeters = static_cast<double>(vertex.x) * kModelScaleMeters;
        const double northMeters = static_cast<double>(-vertex.z) * kModelScaleMeters;
        const double altitudeMeters = static_cast<double>(vertex.y) * kModelScaleMeters;
        const GeoOffset geo = offsetMeters(kMarkerLongitude, kMarkerLatitude, eastMeters, northMeters);
        const custom_map_layers::geo::ScreenPoint projected =
                custom_map_layers::geo::projectToNdc(
                        geo.longitude,
                        geo.latitude,
                        altitudeMeters,
                        params
                );

        vertices.push_back(static_cast<GLfloat>(projected.x));
        vertices.push_back(static_cast<GLfloat>(projected.y));
        vertices.push_back(0.0f);
        vertices.push_back(vertex.u);
        vertices.push_back(vertex.v);
    }

    return vertices;
}

void ModelLayer::resetState() {
    vertexCount_ = 0;
    loaded_ = false;
    didLogFirstRender_ = false;
}

}  // namespace custom_map_layers::layers::model
```

- [ ] **Step 3: Replace JNI exports**

Replace `app/src/main/cpp/lib/jni/custom_map_layers_jni.cpp` with:

```cpp
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <jni.h>

#include <memory>

#include "layers/model/ModelLayer.hpp"

namespace {

constexpr const char* LOG_TAG = "NativeModelLayer";

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_github_arhor_journey_feature_map_viewinterop_NativeModelLayer_createContextNative(
        JNIEnv* env,
        jclass,
        jobject assetManager
) {
    __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "nativeCreateContext");
    AAssetManager* nativeAssetManager = AAssetManager_fromJava(env, assetManager);
    auto layer = std::make_unique<custom_map_layers::layers::model::ModelLayer>(nativeAssetManager);
    return reinterpret_cast<jlong>(layer.release());
}

extern "C" JNIEXPORT void JNICALL
Java_com_github_arhor_journey_feature_map_viewinterop_NativeModelLayer_destroyContextNative(
        JNIEnv*,
        jclass,
        jlong context
) {
    __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "nativeDestroyContext");
    delete reinterpret_cast<custom_map_layers::layers::model::ModelLayer*>(context);
}
```

- [ ] **Step 4: Delete native exclamation layer files**

Delete:

```text
app/src/main/cpp/lib/layers/exclamation/ExclamationLayer.cpp
app/src/main/cpp/lib/layers/exclamation/ExclamationLayer.hpp
```

- [ ] **Step 5: Run compile**

Run:

```shell
./gradlew :app:compileDebugKotlin -q
```

Expected: pass. If `cgltf_load_buffers` fails at runtime for GLB-loaded buffers, remove the `cgltf_load_buffers` call from `GltfModelLoader.cpp`; GLB binary chunks parsed from memory already provide buffer data.

- [ ] **Step 6: Run bridge test**

Run:

```shell
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
```

Expected: pass.

- [ ] **Step 7: Commit**

```shell
git add app/src/main/cpp/lib/layers/model app/src/main/cpp/lib/jni/custom_map_layers_jni.cpp app/src/main/cpp/lib/layers/exclamation/ExclamationLayer.cpp app/src/main/cpp/lib/layers/exclamation/ExclamationLayer.hpp
git commit -m "Render tiger model in native map layer"
```

---

### Task 7: Verify Assets, Build, And Runtime Behavior

**Files:**
- Verify: `app/src/main/assets/models/animal-tiger.glb`
- Verify: `app/src/main/assets/models/textures/colormap.png`

- [ ] **Step 1: Confirm model assets are staged or tracked**

Run:

```shell
git status --short app/src/main/assets/models/animal-tiger.glb app/src/main/assets/models/textures/colormap.png
```

Expected:

```text
A  app/src/main/assets/models/animal-tiger.glb
A  app/src/main/assets/models/textures/colormap.png
```

If the files are not staged, run:

```shell
git add app/src/main/assets/models/animal-tiger.glb app/src/main/assets/models/textures/colormap.png
```

- [ ] **Step 2: Run final compile**

Run:

```shell
./gradlew :app:compileDebugKotlin -q
```

Expected: pass.

- [ ] **Step 3: Run final focused JVM tests**

Run:

```shell
./gradlew :app:testDebugUnitTest --tests "com.github.arhor.journey.feature.map.viewinterop.NativeModelLayerTest"
```

Expected: pass.

- [ ] **Step 4: Build a debug APK if runtime validation will be performed**

Run:

```shell
./gradlew :app:assembleDebug
```

Expected: pass.

- [ ] **Step 5: Runtime validation on device or emulator**

Launch the app, open the map, and inspect logcat:

```shell
/Users/maksimburyshynets/Library/Android/sdk/platform-tools/adb logcat -d | rg "NativeModelLayer|cgltf|GLES"
```

Expected log signals:

```text
NativeModelLayer: initialize
NativeModelLayer: Loaded tiger model vertices=
NativeModelLayer: render
```

Visual expectations:

- The red exclamation mark is absent.
- The tiger is visible at the hard-coded marker coordinate.
- Map zoom, pitch, bearing, and pan do not crash the renderer.
- Logcat has no `NativeModelLayer` missing asset, PNG decode, shader, or GLES error messages.

- [ ] **Step 6: Commit assets if they were not already committed**

```shell
git add app/src/main/assets/models/animal-tiger.glb app/src/main/assets/models/textures/colormap.png
git commit -m "Add tiger model map assets"
```

Skip this commit if the assets were already committed by the user before implementation reaches this step.

---

## Plan Self-Review

Spec coverage:

- Hard-coded tiger GLB marker: Tasks 5, 6, and 7.
- Kotlin `AssetManager` bridge: Tasks 1 and 6.
- `cgltf` rather than custom GLB parsing: Tasks 3 and 5.
- External texture fallback: Task 5.
- GLES texture/buffer helper boundaries: Tasks 4 and 6.
- Exclamation layer replacement: Tasks 2 and 6.
- Focused JVM tests: Tasks 1, 2, 6, and 7.
- Runtime validation: Task 7.

Placeholder scan:

- No unresolved placeholders or unspecified validation steps remain.

Type consistency:

- Kotlin bridge name is `NativeModelLayer`.
- Native JNI symbols use `NativeModelLayer_createContextNative` and `NativeModelLayer_destroyContextNative`.
- Native layer type is `custom_map_layers::layers::model::ModelLayer`.
- CPU model type is `custom_map_layers::gltf::LoadedModel`.
