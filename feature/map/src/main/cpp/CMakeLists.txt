cmake_minimum_required(VERSION 3.22.1)

project(journey_map_custom_layer)

add_library(
    journey_map_custom_layer
    SHARED
    map_custom_layer.cpp
)

find_library(log-lib log)
find_library(glesv2-lib GLESv2)

target_link_libraries(
    journey_map_custom_layer
    ${glesv2-lib}
    ${log-lib}
)
