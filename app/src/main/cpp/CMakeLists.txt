# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
#
# Make sure this version is installed through Android Studio / SDK Manager
# and matches the version configured by Gradle for externalNativeBuild.
cmake_minimum_required(VERSION 4.1.2)

# Declares the project name and enabled languages.
#
# The project name can be accessed via ${PROJECT_NAME}. Since this is the
# top-level CMakeLists.txt, the same value is also available through
# ${CMAKE_PROJECT_NAME}.
#
# LANGUAGES CXX tells CMake this project only needs C++, so C language
# support is not initialized unless added later.
project(custom-map-layers LANGUAGES CXX)

# Enables CMake's built-in dependency fetch helper.
#
# The native build uses this for small source dependencies that should be
# pinned by tag instead of committed into app/src/main/cpp.
include(FetchContent)

# Keep the native target name in one place.
#
# This target name also determines the resulting shared library name:
#     libcustom-map-layers.so
#
# From Kotlin/Java, load it with:
#     System.loadLibrary("custom-map-layers")
set(CUSTOM_MAP_LAYERS_TARGET custom-map-layers)

# Declares the cgltf dependency used by the glTF loader.
#
# cgltf is a single-header library. This project keeps the one
# CGLTF_IMPLEMENTATION translation unit in lib/gltf/cgltf_impl.cpp, while
# CMake fetches the upstream header from the pinned v1.15 tag during native
# configuration.
FetchContent_Declare(
        cgltf
        GIT_REPOSITORY https://github.com/jkuhlmann/cgltf.git
        GIT_TAG v1.15
)

# Downloads and makes the declared cgltf source directory available.
#
# The fetched project does not need to be linked as a separate target here;
# target_include_directories() below points the native library at the fetched
# source root so #include "cgltf.h" resolves to the pinned upstream header.
FetchContent_MakeAvailable(cgltf)

# Creates the native shared library that Gradle will package into the APK.
#
# Sources are added separately via target_sources() below. Keeping target
# creation separate from source declaration makes the file easier to grow
# as JNI, rendering, assets, glTF loading, and geometry code evolve.
add_library(${CUSTOM_MAP_LAYERS_TARGET} SHARED)

# Lists the C++ source files that are compiled into the shared library.
#
# PRIVATE means these files are implementation details of this target.
# They are not exposed to other CMake targets as part of a public API.
target_sources(
        ${CUSTOM_MAP_LAYERS_TARGET}
        PRIVATE

        # JNI entry points called from Kotlin/Java.
        lib/jni/custom_map_layers_jni.cpp

        # MapLibre custom layer implementations.
        lib/layers/model/ModelLayer.cpp

        # Android asset and image loading helpers.
        lib/assets/AssetReader.cpp
        lib/assets/ImageDecoder.cpp

        # glTF / GLB model loading.
        lib/gltf/GltfModelLoader.cpp
        lib/gltf/cgltf_impl.cpp

        # Geographic projection and coordinate conversion helpers.
        lib/geo/WebMercator.cpp

        # OpenGL ES rendering utilities.
        lib/rendering/GlError.cpp
        lib/rendering/GlesProgram.cpp
        lib/rendering/GlTexture.cpp
        lib/rendering/VertexBuffer.cpp
)

# Requests C++20 support for this target.
#
# CMake will add the appropriate compiler flags for the Android NDK compiler.
# PRIVATE means the requirement applies only while building this library.
target_compile_features(
        ${CUSTOM_MAP_LAYERS_TARGET}
        PRIVATE
        cxx_std_20
)

# Makes the selected include directories visible while compiling this target.
#
# include:
#   Public-style project headers, for example ABI mirrors or declarations
#   shared across native implementation files.
#
# lib:
#   Internal implementation headers located near .cpp files.
#
# cgltf_SOURCE_DIR:
#   Header-only cgltf dependency fetched from the pinned upstream tag.
target_include_directories(
        ${CUSTOM_MAP_LAYERS_TARGET}
        PRIVATE
        include
        lib
        ${cgltf_SOURCE_DIR}
)

# Enables stricter compiler diagnostics for this target.
#
# Android NDK uses Clang, so these warning flags are supported.
#
# -Wall / -Wextra:
#   Enable common and additional useful warnings.
#
# -Wpedantic:
#   Warn about non-standard C++ usage.
#
# Release builds treat warnings as errors. This keeps production builds clean
# without making local debug iteration too sharp-clawed.
target_compile_options(
        ${CUSTOM_MAP_LAYERS_TARGET}
        PRIVATE
        -Wall
        -Wextra
        -Wpedantic
        $<$<CONFIG:Release>:-Werror>
)

# Specifies libraries CMake should link to the native target.
#
# These are Android / NDK system libraries:
#
# android:
#   Native Android APIs, including AAssetManager and related platform types.
#
# log:
#   Android logging APIs, for example __android_log_print.
#
# jnigraphics:
#   Native bitmap access APIs, useful when decoding or reading Android Bitmap data.
#
# GLESv3:
#   OpenGL ES 3.x symbols used by the rendering layer.
target_link_libraries(
        ${CUSTOM_MAP_LAYERS_TARGET}
        PRIVATE
        android
        log
        jnigraphics
        GLESv3
)
