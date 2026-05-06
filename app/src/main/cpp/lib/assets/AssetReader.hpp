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
