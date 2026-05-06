#include "GlError.hpp"

#include <GLES3/gl3.h>
#include <android/log.h>

namespace custom_map_layers::rendering {

namespace {

const char* glErrorName(GLenum error) {
    switch (error) {
        case GL_INVALID_ENUM:
            return "GL_INVALID_ENUM";
        case GL_INVALID_VALUE:
            return "GL_INVALID_VALUE";
        case GL_INVALID_OPERATION:
            return "GL_INVALID_OPERATION";
        case GL_INVALID_FRAMEBUFFER_OPERATION:
            return "GL_INVALID_FRAMEBUFFER_OPERATION";
        case GL_OUT_OF_MEMORY:
            return "GL_OUT_OF_MEMORY";
#ifdef GL_CONTEXT_LOST
        case GL_CONTEXT_LOST:
            return "GL_CONTEXT_LOST";
#endif
        default:
            return "GL_UNKNOWN";
    }
}

}  // namespace

void logGlErrors(const char* operation, const char* logTag) {
    GLenum error = GL_NO_ERROR;
    while ((error = glGetError()) != GL_NO_ERROR) {
        __android_log_print(ANDROID_LOG_ERROR, logTag, "%s: %s", operation, glErrorName(error));
    }
}

}  // namespace custom_map_layers::rendering
