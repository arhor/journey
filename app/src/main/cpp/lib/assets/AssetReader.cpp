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
