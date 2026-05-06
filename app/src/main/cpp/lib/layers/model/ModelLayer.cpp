#include "layers/model/ModelLayer.hpp"

#include <android/log.h>

#include <cmath>
#include <utility>

#include "assets/AssetReader.hpp"
#include "assets/ImageDecoder.hpp"
#include "geo/WebMercator.hpp"
#include "gltf/GltfModelLoader.hpp"
#include "rendering/GlError.hpp"

namespace {

constexpr const char* LOG_TAG = "NativeModelLayer";
constexpr double kMarkerLatitude = 54.3744505;
constexpr double kMarkerLongitude = 18.6502754;
constexpr double kEarthRadiusMeters = 6378137.0;
constexpr double kModelScaleMeters = 95.0;

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
    fragColor = texture(u_texture, v_uv);
}
)";

struct GeoOffset {
    double longitude;
    double latitude;
};

GeoOffset offsetMeters(double longitude, double latitude, double eastMeters, double northMeters) {
    constexpr double radiansToDegrees = 180.0 / 3.14159265358979323846264338327950288;
    constexpr double degreesToRadians = 3.14159265358979323846264338327950288 / 180.0;
    const double dLat = northMeters / kEarthRadiusMeters;
    const double dLon = eastMeters / (kEarthRadiusMeters * std::cos(latitude * degreesToRadians));
    return GeoOffset{
            .longitude = longitude + dLon * radiansToDegrees,
            .latitude = latitude + dLat * radiansToDegrees,
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

    if (!didLogFirstRender_) {
        __android_log_print(
                ANDROID_LOG_INFO,
                LOG_TAG,
                "render %.0fx%.0f camera=(%.7f, %.7f) zoom=%.2f bearing=%.4f pitch=%.4f marker=(%.7f, %.7f) vertices=%d",
                params.width,
                params.height,
                params.latitude,
                params.longitude,
                params.zoom,
                params.bearing,
                params.pitch,
                kMarkerLatitude,
                kMarkerLongitude,
                vertexCount_
        );
        didLogFirstRender_ = true;
    }

    const std::vector<GLfloat> vertices = buildProjectedVertices(params);
    vertexCount_ = static_cast<GLsizei>(vertices.size() / 5);
    if (vertexCount_ == 0) {
        return;
    }

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
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glDrawArrays(GL_TRIANGLES, 0, vertexCount_);

    glDisableVertexAttribArray(1);
    glDisableVertexAttribArray(0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);
    glUseProgram(0);
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
        const double eastMeters = static_cast<double>(vertex.x) * kModelScaleMeters;
        const double northMeters = static_cast<double>(-vertex.z) * kModelScaleMeters;
        const double altitudeMeters = static_cast<double>(vertex.y) * kModelScaleMeters;
        const GeoOffset geo = offsetMeters(kMarkerLongitude, kMarkerLatitude, eastMeters, northMeters);
        const custom_map_layers::geo::ScreenPoint projected =
                custom_map_layers::geo::projectToNdc(
                        geo.longitude,
                        geo.latitude,
                        altitudeMeters,
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
