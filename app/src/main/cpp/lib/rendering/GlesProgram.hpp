#pragma once

#include <GLES3/gl3.h>

namespace custom_map_layers::rendering {

class GlesProgram {
public:
    GlesProgram() = default;
    ~GlesProgram() = default;

    GlesProgram(const GlesProgram&) = delete;
    GlesProgram& operator=(const GlesProgram&) = delete;

    bool create(const char* vertexShaderSource, const char* fragmentShaderSource, const char* logTag);
    void reset();
    void forget();

    [[nodiscard]] GLuint handle() const;

private:
    GLuint program_ = 0;
    GLuint vertexShader_ = 0;
    GLuint fragmentShader_ = 0;
};

}  // namespace custom_map_layers::rendering
