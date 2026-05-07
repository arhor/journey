#include <cmath>
#include <iostream>

#include "gltf/TextureCoordinate.hpp"

namespace {

bool isClose(float actual, float expected) {
    return std::fabs(actual - expected) < 0.000001f;
}

}  // namespace

int main() {
    if (!isClose(custom_map_layers::gltf::rendererTextureV(0.0f), 0.0f)) {
        std::cerr << "rendererTextureV should keep glTF v=0 unchanged\n";
        return 1;
    }

    if (!isClose(custom_map_layers::gltf::rendererTextureV(0.8f), 0.8f)) {
        std::cerr << "rendererTextureV should keep tiger colormap samples out of the black top band\n";
        return 1;
    }

    if (!isClose(custom_map_layers::gltf::rendererTextureV(1.0f), 1.0f)) {
        std::cerr << "rendererTextureV should keep glTF v=1 unchanged\n";
        return 1;
    }

    return 0;
}
