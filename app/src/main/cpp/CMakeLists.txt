cmake_minimum_required(VERSION 4.1.2)

project(custom-map-layers LANGUAGES CXX)

add_library(
        custom-map-layers
        SHARED
        lib/geo/WebMercator.cpp
        lib/rendering/GlError.cpp
        lib/rendering/GlesProgram.cpp
        lib/rendering/VertexBuffer.cpp
        native-lib.cpp
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
