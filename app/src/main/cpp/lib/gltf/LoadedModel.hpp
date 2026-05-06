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
