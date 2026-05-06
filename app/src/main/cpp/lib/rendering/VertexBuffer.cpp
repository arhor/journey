#include "VertexBuffer.hpp"

#include "GlError.hpp"

namespace custom_map_layers::rendering {

VertexBuffer::~VertexBuffer() {
    reset();
}

bool VertexBuffer::create(const char* logTag) {
    reset();
    glGenBuffers(1, &buffer_);
    logGlErrors("glGenBuffers", logTag);
    return buffer_ != 0;
}

void VertexBuffer::upload(const std::vector<GLfloat>& vertices) const {
    glBindBuffer(GL_ARRAY_BUFFER, buffer_);
    glBufferData(
            GL_ARRAY_BUFFER,
            static_cast<GLsizeiptr>(vertices.size() * sizeof(GLfloat)),
            vertices.data(),
            GL_DYNAMIC_DRAW
    );
}

void VertexBuffer::bind() const {
    glBindBuffer(GL_ARRAY_BUFFER, buffer_);
}

void VertexBuffer::reset() {
    if (buffer_ != 0) {
        glDeleteBuffers(1, &buffer_);
    }
    buffer_ = 0;
}

GLuint VertexBuffer::handle() const {
    return buffer_;
}

} // namespace custom_map_layers::rendering
