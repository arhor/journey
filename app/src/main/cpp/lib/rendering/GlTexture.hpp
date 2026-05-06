#pragma once

#include <GLES3/gl3.h>

namespace custom_map_layers::rendering {

class GlTexture {
public:
    GlTexture() = default;
    ~GlTexture() = default;

    GlTexture(const GlTexture&) = delete;
    GlTexture& operator=(const GlTexture&) = delete;

    bool createRgba(const unsigned char* pixels, int width, int height, const char* logTag);
    void bind(GLenum textureUnit) const;
    void reset();
    void forget();

    [[nodiscard]] GLuint handle() const;

private:
    GLuint texture_ = 0;
};

}  // namespace custom_map_layers::rendering
