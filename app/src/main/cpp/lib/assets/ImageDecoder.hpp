#pragma once

#include <optional>
#include <vector>

namespace custom_map_layers::assets {

struct DecodedImage {
    int width = 0;
    int height = 0;
    std::vector<unsigned char> rgbaPixels;
};

std::optional<DecodedImage> decodePngRgba(const std::vector<unsigned char>& pngBytes, const char* logTag);

}  // namespace custom_map_layers::assets
