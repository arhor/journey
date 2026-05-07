#include "layers/model/ModelLayer.hpp"

#include <android/log.h>

#include <algorithm>
#include <cmath>
#include <limits>
#include <utility>

#include "assets/AssetReader.hpp"
#include "assets/ImageDecoder.hpp"
#include "geo/WebMercator.hpp"
#include "gltf/GltfModelLoader.hpp"
#include "rendering/GlError.hpp"

namespace {

constexpr const char* LOG_TAG = "NativeModelLayer";
constexpr double kMarkerLatitude = 54.3738000;
constexpr double kMarkerLongitude = 18.6508750;
constexpr double kMarkerAltitudeMeters = 0.0;
constexpr double kModelMetersPerUnit = 45.0;
constexpr double kModelHeadingRadians = 0.0;
constexpr double kPitchedCameraLogThreshold = 0.15;

constexpr const char* kVertexShaderSource = R"(#version 300 es
layout(location = 0) in vec4 a_clip_pos;
layout(location = 1) in vec2 a_uv;
out vec2 v_uv;

void main() {
    gl_Position = a_clip_pos;
    v_uv = a_uv;
}
)";

constexpr const char* kFragmentShaderSource = R"(#version 300 es
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
        const custom_map_layers::gltf::ModelVertex& vertex
) {
    const double modelEast = static_cast<double>(vertex.x) * kModelMetersPerUnit;
    const double modelNorth = static_cast<double>(-vertex.z) * kModelMetersPerUnit;
    const double modelUp = static_cast<double>(vertex.y) * kModelMetersPerUnit;

    const double cosHeading = std::cos(kModelHeadingRadians);
    const double sinHeading = std::sin(kModelHeadingRadians);

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
            .x = projectionMatrix[0] * worldX +
                 projectionMatrix[4] * worldY +
                 projectionMatrix[8] * altitudeMeters +
                 projectionMatrix[12],
            .y = projectionMatrix[1] * worldX +
                 projectionMatrix[5] * worldY +
                 projectionMatrix[9] * altitudeMeters +
                 projectionMatrix[13],
            .z = projectionMatrix[2] * worldX +
                 projectionMatrix[6] * worldY +
                 projectionMatrix[10] * altitudeMeters +
                 projectionMatrix[14],
            .w = projectionMatrix[3] * worldX +
                 projectionMatrix[7] * worldY +
                 projectionMatrix[11] * altitudeMeters +
                 projectionMatrix[15],
    };
}

}  // namespace

namespace custom_map_layers::layers::model {

ModelLayer::ModelLayer(AAssetManager* assetManager) : assetManager_(assetManager) {}

void ModelLayer::initialize() {
    __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "initialize");
    deinitialize();

    if (!program_.create(kVertexShaderSource, kFragmentShaderSource, LOG_TAG)) {
        deinitialize();
        return;
    }

    if (!vertexBuffer_.create(LOG_TAG)) {
        deinitialize();
        return;
    }

    if (!loadModelAndTexture()) {
        deinitialize();
    }
}

void ModelLayer::render(const mbgl::style::CustomLayerRenderParameters& params) {
    if (!loaded_ || program_.handle() == 0 || vertexBuffer_.handle() == 0 || texture_.handle() == 0) {
        return;
    }

    const bool isPitchedCamera = std::abs(params.pitch) > kPitchedCameraLogThreshold;
    const bool shouldLogRender = !didLogFirstRender_ || (!didLogFirstPitchedRender_ && isPitchedCamera);
    if (shouldLogRender) {
        __android_log_print(
                ANDROID_LOG_INFO,
                LOG_TAG,
                "render %.0fx%.0f camera=(%.7f, %.7f) zoom=%.2f bearing=%.4f pitch=%.4f marker=(%.7f, %.7f, %.1fm) vertices=%d",
                params.width,
                params.height,
                params.latitude,
                params.longitude,
                params.zoom,
                params.bearing,
                params.pitch,
                kMarkerLatitude,
                kMarkerLongitude,
                kMarkerAltitudeMeters,
                vertexCount_
        );
        didLogFirstRender_ = true;
        if (isPitchedCamera) {
            didLogFirstPitchedRender_ = true;
        }
    }

    const std::vector<GLfloat> vertices = buildProjectedVertices(params);
    vertexCount_ = static_cast<GLsizei>(vertices.size() / 6);
    if (vertexCount_ == 0) {
        return;
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
                LOG_TAG,
                "model bounds ndc=(%.3f, %.3f)-(%.3f, %.3f)",
                bounds.minX,
                bounds.minY,
                bounds.maxX,
                bounds.maxY
        );
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
    texture_.bind(GL_TEXTURE0);
    const GLint textureUniform = glGetUniformLocation(program_.handle(), "u_texture");
    glUniform1i(textureUniform, 0);

    vertexBuffer_.upload(vertices);
    vertexBuffer_.bind();
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 4, GL_FLOAT, GL_FALSE, 6 * sizeof(GLfloat), nullptr);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(
            1,
            2,
            GL_FLOAT,
            GL_FALSE,
            6 * sizeof(GLfloat),
            reinterpret_cast<const void*>(4 * sizeof(GLfloat))
    );

    glDisable(GL_STENCIL_TEST);
    glDisable(GL_CULL_FACE);
    glDisable(GL_BLEND);
    glDepthMask(GL_TRUE);
    glClearDepthf(1.0f);
    glClear(GL_DEPTH_BUFFER_BIT);
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LEQUAL);
    glDrawArrays(GL_TRIANGLES, 0, vertexCount_);

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
    custom_map_layers::rendering::logGlErrors("render", LOG_TAG);
}

void ModelLayer::contextLost() {
    __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "contextLost");
    texture_.forget();
    vertexBuffer_.forget();
    program_.forget();
    resetState();
}

void ModelLayer::deinitialize() {
    texture_.reset();
    vertexBuffer_.reset();
    program_.reset();
    custom_map_layers::rendering::logGlErrors("deinitialize", LOG_TAG);
    resetState();
}

bool ModelLayer::loadModelAndTexture() {
    const custom_map_layers::gltf::GltfModelLoader loader(assetManager_);
    auto loadedModel = loader.loadTiger(LOG_TAG);
    if (!loadedModel.has_value()) {
        return false;
    }

    const custom_map_layers::assets::AssetReader reader(assetManager_);
    const auto textureBytes = reader.readBytes(loadedModel->texturePath, LOG_TAG);
    if (!textureBytes.has_value()) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Missing texture asset: %s", loadedModel->texturePath.c_str());
        return false;
    }

    const auto decoded = custom_map_layers::assets::decodePngRgba(*textureBytes, LOG_TAG);
    if (!decoded.has_value()) {
        return false;
    }

    if (!texture_.createRgba(decoded->rgbaPixels.data(), decoded->width, decoded->height, LOG_TAG)) {
        return false;
    }

    model_ = std::move(*loadedModel);
    vertexCount_ = static_cast<GLsizei>(model_.triangleVertices.size());
    loaded_ = true;
    __android_log_print(
            ANDROID_LOG_INFO,
            LOG_TAG,
            "Loaded tiger model vertices=%zu texture=%s",
            model_.triangleVertices.size(),
            model_.texturePath.c_str()
    );
    return true;
}

std::vector<GLfloat> ModelLayer::buildProjectedVertices(
        const mbgl::style::CustomLayerRenderParameters& params
) const {
    std::vector<GLfloat> vertices;
    vertices.reserve(model_.triangleVertices.size() * 6);

    const double worldSize = 512.0 * std::pow(2.0, params.zoom);
    const double worldPixelsPerMeter =
            custom_map_layers::geo::metersToMercatorUnits(1.0, kMarkerLatitude) * worldSize;
    const double originX = custom_map_layers::geo::longitudeToMercatorX(kMarkerLongitude) * worldSize;
    const double originY = custom_map_layers::geo::latitudeToMercatorY(kMarkerLatitude) * worldSize;

    for (const custom_map_layers::gltf::ModelVertex& vertex : model_.triangleVertices) {
        const custom_map_layers::geo::LocalMeters localMeters = rotateLocalModelMeters(vertex);
        const double worldX = originX + localMeters.east * worldPixelsPerMeter;
        const double worldY = originY - localMeters.north * worldPixelsPerMeter;
        const double altitudeMeters = kMarkerAltitudeMeters + localMeters.up;
        const ClipPosition clipPosition = projectWorldToClip(
                params.projectionMatrix,
                worldX,
                worldY,
                altitudeMeters
        );

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
    vertexCount_ = 0;
    loaded_ = false;
    didLogFirstRender_ = false;
    didLogFirstPitchedRender_ = false;
}

}  // namespace custom_map_layers::layers::model
