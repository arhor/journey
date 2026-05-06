#pragma once

#include <GLES3/gl3.h>

#include <vector>

namespace custom_map_layers::rendering {

class VertexBuffer {
public:
    VertexBuffer() = default;
    ~VertexBuffer();

    VertexBuffer(const VertexBuffer&) = delete;
    VertexBuffer& operator=(const VertexBuffer&) = delete;

    bool create(const char* logTag);
    void upload(const std::vector<GLfloat>& vertices) const;
    void bind() const;
    void reset();

    [[nodiscard]] GLuint handle() const;

private:
    GLuint buffer_ = 0;
};

} // namespace custom_map_layers::rendering
