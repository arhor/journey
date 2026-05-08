#include "layers/model/ModelLayer.hpp"

#include <android/log.h>

#include <algorithm>
#include <cmath>
#include <limits>
#include <memory>
#include <utility>

#include "assets/AssetReader.hpp"
#include "assets/ImageDecoder.hpp"
#include "geo/WebMercator.hpp"
#include "gltf/GltfModelLoader.hpp"
#include "rendering/GlError.hpp"

namespace {

constexpr const char* log_tag = "NativeModelLayer";
constexpr double pitched_camera_log_threshold = 0.15;

constexpr const char* vertex_shader_source = R"(#version 300 es
layout(location = 0) in vec4 a_clip_pos;
layout(location = 1) in vec2 a_uv;
out vec2 v_uv;

void main() {
    gl_Position = a_clip_pos;
    v_uv = a_uv;
}
)";

constexpr const char* fragment_shader_source = R"(#version 300 es
precision highp float;
in vec2 v_uv;
uniform sampler2D u_texture;
out highp vec4 fragColor;

void main() {
    vec4 sampled = texture(u_texture, v_uv);
    fragColor = vec4(sampled.rgb, 1.0);
}
)";

struct NdcBounds {
    double minX = std::numeric_limits<double>::max();
    double minY = std::numeric_limits<double>::max();
    double maxX = std::numeric_limits<double>::lowest();
    double maxY = std::numeric_limits<double>::lowest();
};

struct ClipPosition {
    double x = 0.0;
    double y = 0.0;
    double z = 0.0;
    double w = 1.0;
};

custom_map_layers::geo::LocalMeters rotateLocalModelMeters(
        const custom_map_layers::layers::model::ModelInstance& instance,
        const custom_map_layers::gltf::ModelVertex& vertex
) {
    const double modelEast = static_cast<double>(vertex.x) * instance.scaleMetersPerModelUnit;
    const double modelNorth = static_cast<double>(-vertex.z) * instance.scaleMetersPerModelUnit;
    const double modelUp = static_cast<double>(vertex.y) * instance.scaleMetersPerModelUnit;

    const double cosHeading = std::cos(instance.headingRadians);
    const double sinHeading = std::sin(instance.headingRadians);

    return custom_map_layers::geo::LocalMeters{
            .east = modelEast * cosHeading - modelNorth * sinHeading,
            .north = modelEast * sinHeading + modelNorth * cosHeading,
            .up = modelUp,
    };
}

ClipPosition projectWorldToClip(
        const std::array<double, 16>& projectionMatrix,
        double worldX,
        double worldY,
        double altitudeMeters
) {
    return ClipPosition{
            .x = projectionMatrix[0] * worldX + projectionMatrix[4] * worldY + projectionMatrix[8] * altitudeMeters +
                 projectionMatrix[12],
            .y = projectionMatrix[1] * worldX + projectionMatrix[5] * worldY + projectionMatrix[9] * altitudeMeters +
                 projectionMatrix[13],
            .z = projectionMatrix[2] * worldX + projectionMatrix[6] * worldY + projectionMatrix[10] * altitudeMeters +
                 projectionMatrix[14],
            .w = projectionMatrix[3] * worldX + projectionMatrix[7] * worldY + projectionMatrix[11] * altitudeMeters +
                 projectionMatrix[15],
    };
}

}  // namespace

namespace custom_map_layers::layers::model {

ModelLayer::ModelLayer(AAssetManager* assetManager, std::vector<ModelInstance> instances)
    : assetManager_(assetManager), instances_(std::move(instances)) {}

void ModelLayer::initialize() {
    __android_log_write(ANDROID_LOG_INFO, log_tag, "initialize");
    deinitialize();

    if (!program_.create(vertex_shader_source, fragment_shader_source, log_tag)) {
        deinitialize();
        return;
    }

    if (!vertexBuffer_.create(log_tag)) {
        deinitialize();
        return;
    }

    if (!loadModelResources()) {
        deinitialize();
    }
}

void ModelLayer::render(const mbgl::style::CustomLayerRenderParameters& params) {
    if (!loaded_ || program_.handle() == 0 || vertexBuffer_.handle() == 0 || instances_.empty()) {
        return;
    }

    const bool isPitchedCamera = std::abs(params.pitch) > pitched_camera_log_threshold;
    const bool shouldLogRender = !didLogFirstRender_ || (!didLogFirstPitchedRender_ && isPitchedCamera);
    if (shouldLogRender) {
        __android_log_print(
                ANDROID_LOG_INFO,
                log_tag,
                "render %.0fx%.0f camera=(%.7f, %.7f) zoom=%.2f bearing=%.4f pitch=%.4f instances=%zu",
                params.width,
                params.height,
                params.latitude,
                params.longitude,
                params.zoom,
                params.bearing,
                params.pitch,
                instances_.size()
        );
        didLogFirstRender_ = true;
        if (isPitchedCamera) {
            didLogFirstPitchedRender_ = true;
        }
    }

    GLint previousProgram = 0;
    GLint previousArrayBuffer = 0;
    GLint previousActiveTexture = 0;
    GLint previousTexture2d = 0;
    GLint previousBlendSrcRgb = 0;
    GLint previousBlendDstRgb = 0;
    GLint previousBlendSrcAlpha = 0;
    GLint previousBlendDstAlpha = 0;
    GLint previousDepthFunc = GL_LESS;
    GLfloat previousDepthClearValue = 1.0f;
    GLboolean previousDepthMask = GL_TRUE;
    const GLboolean wasStencilEnabled = glIsEnabled(GL_STENCIL_TEST);
    const GLboolean wasDepthEnabled = glIsEnabled(GL_DEPTH_TEST);
    const GLboolean wasCullEnabled = glIsEnabled(GL_CULL_FACE);
    const GLboolean wasBlendEnabled = glIsEnabled(GL_BLEND);
    glGetIntegerv(GL_CURRENT_PROGRAM, &previousProgram);
    glGetIntegerv(GL_ARRAY_BUFFER_BINDING, &previousArrayBuffer);
    glGetIntegerv(GL_ACTIVE_TEXTURE, &previousActiveTexture);
    glGetIntegerv(GL_BLEND_SRC_RGB, &previousBlendSrcRgb);
    glGetIntegerv(GL_BLEND_DST_RGB, &previousBlendDstRgb);
    glGetIntegerv(GL_BLEND_SRC_ALPHA, &previousBlendSrcAlpha);
    glGetIntegerv(GL_BLEND_DST_ALPHA, &previousBlendDstAlpha);
    glGetIntegerv(GL_DEPTH_FUNC, &previousDepthFunc);
    glGetFloatv(GL_DEPTH_CLEAR_VALUE, &previousDepthClearValue);
    glGetBooleanv(GL_DEPTH_WRITEMASK, &previousDepthMask);
    glActiveTexture(GL_TEXTURE0);
    glGetIntegerv(GL_TEXTURE_BINDING_2D, &previousTexture2d);

    glUseProgram(program_.handle());
    const GLint textureUniform = glGetUniformLocation(program_.handle(), "u_texture");
    glUniform1i(textureUniform, 0);
    glEnableVertexAttribArray(0);
    glEnableVertexAttribArray(1);
    glDisable(GL_STENCIL_TEST);
    glDisable(GL_CULL_FACE);
    glDisable(GL_BLEND);
    glDepthMask(GL_TRUE);
    glClearDepthf(1.0f);
    glClear(GL_DEPTH_BUFFER_BIT);
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LEQUAL);

    vertexCount_ = 0;
    for (const ModelInstance& instance : instances_) {
        const auto resourceIterator = resourcesByAssetPath_.find(instance.assetPath);
        if (resourceIterator == resourcesByAssetPath_.end()) {
            continue;
        }

        const CachedModelResource& resource = resourceIterator->second;
        if (resource.texture == nullptr || resource.texture->handle() == 0) {
            continue;
        }

        const std::vector<GLfloat> vertices = buildProjectedVertices(params, instance, resource.model);
        const GLsizei instanceVertexCount = static_cast<GLsizei>(vertices.size() / 6);
        if (instanceVertexCount == 0) {
            continue;
        }

        if (shouldLogRender) {
            NdcBounds bounds;
            for (size_t vertexOffset = 0; vertexOffset < vertices.size(); vertexOffset += 6) {
                const auto w = static_cast<double>(vertices[vertexOffset + 3]);
                if (w == 0.0) {
                    continue;
                }
                const double ndcX = static_cast<double>(vertices[vertexOffset]) / w;
                const double ndcY = static_cast<double>(vertices[vertexOffset + 1]) / w;
                bounds.minX = std::min(bounds.minX, ndcX);
                bounds.maxX = std::max(bounds.maxX, ndcX);
                bounds.minY = std::min(bounds.minY, ndcY);
                bounds.maxY = std::max(bounds.maxY, ndcY);
            }
            __android_log_print(
                    ANDROID_LOG_INFO,
                    log_tag,
                    "model asset=%s bounds ndc=(%.3f, %.3f)-(%.3f, %.3f)",
                    instance.assetPath.c_str(),
                    bounds.minX,
                    bounds.minY,
                    bounds.maxX,
                    bounds.maxY
            );
        }

        resource.texture->bind(GL_TEXTURE0);
        vertexBuffer_.upload(vertices);
        vertexBuffer_.bind();
        glVertexAttribPointer(0, 4, GL_FLOAT, GL_FALSE, 6 * sizeof(GLfloat), nullptr);
        glVertexAttribPointer(
                1,
                2,
                GL_FLOAT,
                GL_FALSE,
                6 * sizeof(GLfloat),
                reinterpret_cast<const void*>(4 * sizeof(GLfloat))
        );
        glDrawArrays(GL_TRIANGLES, 0, instanceVertexCount);
        vertexCount_ += instanceVertexCount;
    }

    if (shouldLogRender) {
        __android_log_print(ANDROID_LOG_INFO, log_tag, "rendered vertices=%d", vertexCount_);
    }

    glDisableVertexAttribArray(1);
    glDisableVertexAttribArray(0);
    if (wasStencilEnabled) {
        glEnable(GL_STENCIL_TEST);
    } else {
        glDisable(GL_STENCIL_TEST);
    }
    if (wasDepthEnabled) {
        glEnable(GL_DEPTH_TEST);
    } else {
        glDisable(GL_DEPTH_TEST);
    }
    if (wasCullEnabled) {
        glEnable(GL_CULL_FACE);
    } else {
        glDisable(GL_CULL_FACE);
    }
    if (wasBlendEnabled) {
        glEnable(GL_BLEND);
    } else {
        glDisable(GL_BLEND);
    }
    glDepthFunc(static_cast<GLenum>(previousDepthFunc));
    glClearDepthf(previousDepthClearValue);
    glDepthMask(previousDepthMask);
    glBlendFuncSeparate(
            static_cast<GLenum>(previousBlendSrcRgb),
            static_cast<GLenum>(previousBlendDstRgb),
            static_cast<GLenum>(previousBlendSrcAlpha),
            static_cast<GLenum>(previousBlendDstAlpha)
    );
    glBindBuffer(GL_ARRAY_BUFFER, static_cast<GLuint>(previousArrayBuffer));
    glBindTexture(GL_TEXTURE_2D, static_cast<GLuint>(previousTexture2d));
    glActiveTexture(static_cast<GLenum>(previousActiveTexture));
    glUseProgram(static_cast<GLuint>(previousProgram));
    custom_map_layers::rendering::logGlErrors("render", log_tag);
}

void ModelLayer::contextLost() {
    __android_log_write(ANDROID_LOG_INFO, log_tag, "contextLost");
    for (auto& [assetPath, resource] : resourcesByAssetPath_) {
        (void)assetPath;
        if (resource.texture != nullptr) {
            resource.texture->forget();
        }
    }
    resourcesByAssetPath_.clear();
    vertexBuffer_.forget();
    program_.forget();
    resetState();
}

void ModelLayer::deinitialize() {
    for (auto& [assetPath, resource] : resourcesByAssetPath_) {
        (void)assetPath;
        if (resource.texture != nullptr) {
            resource.texture->reset();
        }
    }
    resourcesByAssetPath_.clear();
    vertexBuffer_.reset();
    program_.reset();
    custom_map_layers::rendering::logGlErrors("deinitialize", log_tag);
    resetState();
}

bool ModelLayer::loadModelAndTexture(const std::string& assetPath) {
    if (resourcesByAssetPath_.find(assetPath) != resourcesByAssetPath_.end()) {
        return true;
    }

    const custom_map_layers::gltf::GltfModelLoader loader(assetManager_);
    const custom_map_layers::assets::AssetReader reader(assetManager_);
    auto loadedModel = loader.load(assetPath, log_tag);
    if (!loadedModel.has_value()) {
        return false;
    }

    const auto textureBytes = reader.readBytes(loadedModel->texturePath, log_tag);
    if (!textureBytes.has_value()) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                log_tag,
                "Missing texture asset: %s",
                loadedModel->texturePath.c_str()
        );
        return false;
    }

    const auto decoded = custom_map_layers::assets::decodePngRgba(*textureBytes, log_tag);
    if (!decoded.has_value()) {
        return false;
    }

    auto texture = std::make_unique<rendering::GlTexture>();
    if (!texture->createRgba(decoded->rgbaPixels.data(), decoded->width, decoded->height, log_tag)) {
        return false;
    }

    const GLsizei resourceVertexCount = static_cast<GLsizei>(loadedModel->triangleVertices.size());
    resourcesByAssetPath_.emplace(
            assetPath,
            CachedModelResource{
                    .model = std::move(*loadedModel),
                    .texture = std::move(texture),
                    .vertexCount = resourceVertexCount,
            }
    );
    __android_log_print(
            ANDROID_LOG_INFO,
            log_tag,
            "Loaded model asset=%s vertices=%d",
            assetPath.c_str(),
            resourceVertexCount
    );
    return true;
}

bool ModelLayer::loadModelResources() {
    resourcesByAssetPath_.clear();
    loaded_ = false;
    if (instances_.empty()) {
        return false;
    }

    bool hasLoadedModel = false;
    for (const ModelInstance& instance : instances_) {
        if (instance.assetPath.empty()) {
            __android_log_write(ANDROID_LOG_ERROR, log_tag, "Missing model asset path for instance");
            continue;
        }
        if (!loadModelAndTexture(instance.assetPath)) {
            __android_log_print(
                    ANDROID_LOG_ERROR,
                    log_tag,
                    "Failed to load model asset: %s",
                    instance.assetPath.c_str()
            );
            continue;
        }
        hasLoadedModel = true;
    }

    loaded_ = hasLoadedModel;
    return loaded_;
}

std::vector<GLfloat> ModelLayer::buildProjectedVertices(
        const mbgl::style::CustomLayerRenderParameters& params,
        const ModelInstance& instance,
        const gltf::LoadedModel& model
) const {

    std::vector<GLfloat> vertices;
    vertices.reserve(model.triangleVertices.size() * 6);

    const double worldSize = 512.0 * std::pow(2.0, params.zoom);
    const double worldPixelsPerMeter =
            custom_map_layers::geo::metersToMercatorUnits(1.0, instance.latitude) * worldSize;
    const double originX = custom_map_layers::geo::longitudeToMercatorX(instance.longitude) * worldSize;
    const double originY = custom_map_layers::geo::latitudeToMercatorY(instance.latitude) * worldSize;

    for (const custom_map_layers::gltf::ModelVertex& vertex : model.triangleVertices) {
        const custom_map_layers::geo::LocalMeters localMeters = rotateLocalModelMeters(instance, vertex);
        const double worldX = originX + localMeters.east * worldPixelsPerMeter;
        const double worldY = originY - localMeters.north * worldPixelsPerMeter;
        const double altitudeMeters = instance.altitudeMeters + localMeters.up;
        const ClipPosition clipPosition = projectWorldToClip(params.projectionMatrix, worldX, worldY, altitudeMeters);

        vertices.push_back(static_cast<GLfloat>(clipPosition.x));
        vertices.push_back(static_cast<GLfloat>(clipPosition.y));
        vertices.push_back(static_cast<GLfloat>(clipPosition.z));
        vertices.push_back(static_cast<GLfloat>(clipPosition.w));
        vertices.push_back(vertex.u);
        vertices.push_back(vertex.v);
    }

    return vertices;
}

void ModelLayer::resetState() {
    resourcesByAssetPath_.clear();
    vertexCount_ = 0;
    loaded_ = false;
    didLogFirstRender_ = false;
    didLogFirstPitchedRender_ = false;
}

}  // namespace custom_map_layers::layers::model
