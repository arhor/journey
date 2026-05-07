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

constexpr const char* kVertexShaderSource = R"(#version 300 es
layout(location = 0) in vec3 a_pos;
layout(location = 1) in vec2 a_uv;
out vec2 v_uv;

void main() {
    gl_Position = vec4(a_pos, 1.0);
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

    const bool shouldLogFirstRender = !didLogFirstRender_;
    if (shouldLogFirstRender) {
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
    }

    const std::vector<GLfloat> vertices = buildProjectedVertices(params);
    vertexCount_ = static_cast<GLsizei>(vertices.size() / 5);
    if (vertexCount_ == 0) {
        return;
    }

    if (shouldLogFirstRender) {
        NdcBounds bounds;
        for (size_t vertexOffset = 0; vertexOffset < vertices.size(); vertexOffset += 5) {
            bounds.minX = std::min(bounds.minX, static_cast<double>(vertices[vertexOffset]));
            bounds.maxX = std::max(bounds.maxX, static_cast<double>(vertices[vertexOffset]));
            bounds.minY = std::min(bounds.minY, static_cast<double>(vertices[vertexOffset + 1]));
            bounds.maxY = std::max(bounds.maxY, static_cast<double>(vertices[vertexOffset + 1]));
        }
        __android_log_print(
                ANDROID_LOG_INFO,
                LOG_TAG,
                "projected bounds ndc=(%.3f, %.3f)-(%.3f, %.3f)",
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
    glActiveTexture(GL_TEXTURE0);
    glGetIntegerv(GL_TEXTURE_BINDING_2D, &previousTexture2d);

    glUseProgram(program_.handle());
    texture_.bind(GL_TEXTURE0);
    const GLint textureUniform = glGetUniformLocation(program_.handle(), "u_texture");
    glUniform1i(textureUniform, 0);

    vertexBuffer_.upload(vertices);
    vertexBuffer_.bind();
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 5 * sizeof(GLfloat), nullptr);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(
            1,
            2,
            GL_FLOAT,
            GL_FALSE,
            5 * sizeof(GLfloat),
            reinterpret_cast<const void*>(3 * sizeof(GLfloat))
    );

    glDisable(GL_STENCIL_TEST);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
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
    vertices.reserve(model_.triangleVertices.size() * 5);

    for (const custom_map_layers::gltf::ModelVertex& vertex : model_.triangleVertices) {
        const custom_map_layers::geo::LocalMeters localMeters = rotateLocalModelMeters(vertex);
        const custom_map_layers::geo::ScreenPoint projected =
                custom_map_layers::geo::projectMetersOffsetToNdc(
                        kMarkerLongitude,
                        kMarkerLatitude,
                        kMarkerAltitudeMeters,
                        localMeters,
                        params
                );

        vertices.push_back(static_cast<GLfloat>(projected.x));
        vertices.push_back(static_cast<GLfloat>(projected.y));
        vertices.push_back(0.0f);
        vertices.push_back(vertex.u);
        vertices.push_back(vertex.v);
    }

    return vertices;
}

void ModelLayer::resetState() {
    vertexCount_ = 0;
    loaded_ = false;
    didLogFirstRender_ = false;
}

}  // namespace custom_map_layers::layers::model
