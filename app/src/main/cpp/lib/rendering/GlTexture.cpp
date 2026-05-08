#include "rendering/GlTexture.hpp"

#include "rendering/GlError.hpp"

namespace custom_map_layers::rendering {

bool GlTexture::createRgba(const unsigned char* pixels, int width, int height, const char* logTag) {
    reset();
    glGenTextures(1, &texture_);
    if (texture_ == 0) {
        logGlErrors("glGenTextures", logTag);
        return false;
    }

    glBindTexture(GL_TEXTURE_2D, texture_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    glGenerateMipmap(GL_TEXTURE_2D);
    glBindTexture(GL_TEXTURE_2D, 0);
    logGlErrors("createRgba", logTag);
    return texture_ != 0;
}

void GlTexture::bind(GLenum textureUnit) const {
    glActiveTexture(textureUnit);
    glBindTexture(GL_TEXTURE_2D, texture_);
}

void GlTexture::reset() {
    if (texture_ != 0) {
        glDeleteTextures(1, &texture_);
        texture_ = 0;
    }
}

void GlTexture::forget() {
    texture_ = 0;
}

GLuint GlTexture::handle() const {
    return texture_;
}

}  // namespace custom_map_layers::rendering
