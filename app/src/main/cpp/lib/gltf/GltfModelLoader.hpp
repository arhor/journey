#pragma once

#include <android/asset_manager.h>

#include <optional>

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
