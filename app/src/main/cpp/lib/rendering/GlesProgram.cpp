#include "GlesProgram.hpp"

#include <android/log.h>

#include <algorithm>
#include <cstddef>
#include <string>

#include "GlError.hpp"

namespace custom_map_layers::rendering {

namespace {

bool checkShader(GLuint shader, const char* label, const char* logTag) {
    GLint compiled = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (compiled == GL_TRUE) {
        return true;
    }

    GLint length = 0;
    glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &length);
    std::string log(static_cast<std::size_t>(std::max(length, 1)), '\0');
    glGetShaderInfoLog(shader, length, nullptr, log.data());
    __android_log_print(ANDROID_LOG_ERROR, logTag, "%s shader compile failed: %s", label, log.c_str());
    return false;
}

bool checkProgram(GLuint program, const char* logTag) {
    GLint linked = GL_FALSE;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (linked == GL_TRUE) {
        return true;
    }

    GLint length = 0;
    glGetProgramiv(program, GL_INFO_LOG_LENGTH, &length);
    std::string log(static_cast<std::size_t>(std::max(length, 1)), '\0');
    glGetProgramInfoLog(program, length, nullptr, log.data());
    __android_log_print(ANDROID_LOG_ERROR, logTag, "Program link failed: %s", log.c_str());
    return false;
}

GLuint compileShader(GLenum type, const char* source, const char* label, const char* logTag) {
    const GLuint shader = glCreateShader(type);
    if (shader == 0) {
        logGlErrors("glCreateShader", logTag);
        return 0;
    }

    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    logGlErrors("glCompileShader", logTag);

    if (!checkShader(shader, label, logTag)) {
        glDeleteShader(shader);
        return 0;
    }

    return shader;
}

}  // namespace

bool GlesProgram::create(const char* vertexShaderSource, const char* fragmentShaderSource, const char* logTag) {
    reset();

    vertexShader_ = compileShader(GL_VERTEX_SHADER, vertexShaderSource, "Vertex", logTag);
    fragmentShader_ = compileShader(GL_FRAGMENT_SHADER, fragmentShaderSource, "Fragment", logTag);
    if (vertexShader_ == 0 || fragmentShader_ == 0) {
        reset();
        return false;
    }

    program_ = glCreateProgram();
    if (program_ == 0) {
        logGlErrors("glCreateProgram", logTag);
        reset();
        return false;
    }

    glAttachShader(program_, vertexShader_);
    glAttachShader(program_, fragmentShader_);
    glLinkProgram(program_);
    logGlErrors("glLinkProgram", logTag);

    if (!checkProgram(program_, logTag)) {
        reset();
        return false;
    }

    return true;
}

void GlesProgram::reset() {
    if (program_ != 0 && vertexShader_ != 0) {
        glDetachShader(program_, vertexShader_);
    }
    if (program_ != 0 && fragmentShader_ != 0) {
        glDetachShader(program_, fragmentShader_);
    }
    if (vertexShader_ != 0) {
        glDeleteShader(vertexShader_);
    }
    if (fragmentShader_ != 0) {
        glDeleteShader(fragmentShader_);
    }
    if (program_ != 0) {
        glDeleteProgram(program_);
    }

    forget();
}

void GlesProgram::forget() {
    program_ = 0;
    vertexShader_ = 0;
    fragmentShader_ = 0;
}

GLuint GlesProgram::handle() const {
    return program_;
}

}  // namespace custom_map_layers::rendering
