#pragma once

#include <GLES3/gl3.h>

#include <vector>

#include "custom_map_layers/maplibre/custom_layer_host.hpp"
#include "rendering/GlesProgram.hpp"
#include "rendering/VertexBuffer.hpp"

namespace custom_map_layers::layers::exclamation {

class ExclamationLayer final : public mbgl::style::CustomLayerHost {
public:
    void initialize() override;
    void render(const mbgl::style::CustomLayerRenderParameters&) override;
    void contextLost() override;
    void deinitialize() override;

private:
    void resetState();

    rendering::GlesProgram program_;
    rendering::VertexBuffer vertexBuffer_;
    GLsizei vertexCount_ = 0;
    bool didLogFirstRender_ = false;
    std::vector<GLfloat> vertices_;
};

}  // namespace custom_map_layers::layers::exclamation
