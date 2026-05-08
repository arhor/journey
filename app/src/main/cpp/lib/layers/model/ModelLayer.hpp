#pragma once

#include <GLES3/gl3.h>
#include <android/asset_manager.h>

#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

#include "custom_map_layers/maplibre/custom_layer_host.hpp"
#include "gltf/LoadedModel.hpp"
#include "layers/model/ModelInstance.hpp"
#include "rendering/GlesProgram.hpp"
#include "rendering/GlTexture.hpp"
#include "rendering/VertexBuffer.hpp"

namespace custom_map_layers::layers::model {

class ModelLayer final : public mbgl::style::CustomLayerHost {
public:
    ModelLayer(AAssetManager* assetManager, std::vector<ModelInstance> instances);

    void initialize() override;
    void render(const mbgl::style::CustomLayerRenderParameters& params) override;
    void contextLost() override;
    void deinitialize() override;

private:
    struct CachedModelResource {
        gltf::LoadedModel model;
        std::unique_ptr<rendering::GlTexture> texture;
        GLsizei vertexCount = 0;
    };

    void resetState();
    bool loadModelAndTexture(const std::string& assetPath);
    bool loadModelResources();
    [[nodiscard]] std::vector<GLfloat> buildProjectedVertices(
            const mbgl::style::CustomLayerRenderParameters& params,
            const ModelInstance& instance,
            const gltf::LoadedModel& model
    ) const;

    AAssetManager* assetManager_;
    rendering::GlesProgram program_;
    rendering::VertexBuffer vertexBuffer_;
    std::vector<ModelInstance> instances_;
    std::unordered_map<std::string, CachedModelResource> resourcesByAssetPath_;
    GLsizei vertexCount_ = 0;
    bool loaded_ = false;
    bool didLogFirstRender_ = false;
    bool didLogFirstPitchedRender_ = false;
};

}  // namespace custom_map_layers::layers::model
