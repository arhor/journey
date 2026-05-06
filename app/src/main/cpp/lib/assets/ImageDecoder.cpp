#include "assets/ImageDecoder.hpp"

#include <android/bitmap.h>
#include <android/imagedecoder.h>
#include <android/log.h>

namespace custom_map_layers::assets {

std::optional<DecodedImage> decodePngRgba(
        const std::vector<unsigned char>& pngBytes,
        const char* logTag
) {
    AImageDecoder* decoder = nullptr;
    int result = AImageDecoder_createFromBuffer(
            pngBytes.data(),
            pngBytes.size(),
            &decoder
    );
    if (result != ANDROID_IMAGE_DECODER_SUCCESS || decoder == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, logTag, "Failed to create image decoder: %d", result);
        return std::nullopt;
    }

    const AImageDecoderHeaderInfo* header = AImageDecoder_getHeaderInfo(decoder);
    const int width = AImageDecoderHeaderInfo_getWidth(header);
    const int height = AImageDecoderHeaderInfo_getHeight(header);
    AImageDecoder_setAndroidBitmapFormat(decoder, ANDROID_BITMAP_FORMAT_RGBA_8888);

    const size_t stride = static_cast<size_t>(width) * 4U;
    const size_t size = stride * static_cast<size_t>(height);
    std::vector<unsigned char> pixels(size);
    result = AImageDecoder_decodeImage(decoder, pixels.data(), stride, size);
    AImageDecoder_delete(decoder);

    if (result != ANDROID_IMAGE_DECODER_SUCCESS) {
        __android_log_print(ANDROID_LOG_ERROR, logTag, "Failed to decode image: %d", result);
        return std::nullopt;
    }

    return DecodedImage{
            .width = width,
            .height = height,
            .rgbaPixels = std::move(pixels),
    };
}

}  // namespace custom_map_layers::assets
