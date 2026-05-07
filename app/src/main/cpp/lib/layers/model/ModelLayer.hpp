#pragma once

#include <GLES3/gl3.h>
#include <android/asset_manager.h>

#include <vector>

#include "custom_map_layers/maplibre/custom_layer_host.hpp"
#include "gltf/LoadedModel.hpp"
#include "rendering/GlesProgram.hpp"
#include "rendering/GlTexture.hpp"
#include "rendering/VertexBuffer.hpp"

namespace custom_map_layers::layers::model {

class ModelLayer final : public mbgl::style::CustomLayerHost {
public:
    explicit ModelLayer(AAssetManager* assetManager);

    void initialize() override;
    void render(const mbgl::style::CustomLayerRenderParameters& params) override;
    void contextLost() override;
    void deinitialize() override;

private:
    void resetState();
    bool loadModelAndTexture();
    std::vector<GLfloat> buildProjectedVertices(const mbgl::style::CustomLayerRenderParameters& params) const;

    AAssetManager* assetManager_;
    rendering::GlesProgram program_;
    rendering::VertexBuffer vertexBuffer_;
    rendering::GlTexture texture_;
    gltf::LoadedModel model_;
    GLsizei vertexCount_ = 0;
    bool loaded_ = false;
    bool didLogFirstRender_ = false;
    bool didLogFirstPitchedRender_ = false;
};

}  // namespace custom_map_layers::layers::model
