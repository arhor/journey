#pragma once

#include <android/asset_manager.h>

#include <optional>
#include <string>

#include "gltf/LoadedModel.hpp"

namespace custom_map_layers::gltf {

class GltfModelLoader {
public:
    explicit GltfModelLoader(AAssetManager* assetManager);

    [[nodiscard]] std::optional<LoadedModel> load(const std::string& assetPath, const char* logTag) const;

private:
    AAssetManager* assetManager_;
};

}  // namespace custom_map_layers::gltf
