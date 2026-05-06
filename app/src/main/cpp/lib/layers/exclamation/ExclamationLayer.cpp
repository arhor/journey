#include "layers/exclamation/ExclamationLayer.hpp"

#include "geo/WebMercator.hpp"
#include "rendering/GlError.hpp"

#include <android/log.h>

#include <cmath>

namespace {

constexpr const char* LOG_TAG = "NativeExclamationLayer";
constexpr double kCircleLatitude = 54.3744505;
constexpr double kCircleLongitude = 18.6502754;
constexpr double kStemHeightMeters = 160.0;
constexpr double kStemWidthMeters = 28.0;
constexpr double kDotSizeMeters = 44.0;
constexpr double kDotGapMeters = 24.0;

constexpr const char* kVertexShaderSource = R"(#version 300 es
layout(location = 0) in vec3 a_pos;

void main() {
    gl_Position = vec4(a_pos, 1.0);
}
)";

constexpr const char* kFragmentShaderSource = R"(#version 300 es
precision highp float;
out highp vec4 fragColor;

void main() {
    fragColor = vec4(1.0, 0.0, 0.0, 1.0);
}
)";

using custom_map_layers::geo::metersToMercatorUnits;
using custom_map_layers::geo::projectToNdc;
using custom_map_layers::geo::ScreenPoint;

void appendTriangle(
        std::vector<GLfloat>& vertices,
        const ScreenPoint& a,
        const ScreenPoint& b,
        const ScreenPoint& c
) {
    vertices.push_back(static_cast<GLfloat>(a.x));
    vertices.push_back(static_cast<GLfloat>(a.y));
    vertices.push_back(0.0f);
    vertices.push_back(static_cast<GLfloat>(b.x));
    vertices.push_back(static_cast<GLfloat>(b.y));
    vertices.push_back(0.0f);
    vertices.push_back(static_cast<GLfloat>(c.x));
    vertices.push_back(static_cast<GLfloat>(c.y));
    vertices.push_back(0.0f);
}

void appendQuad(
        std::vector<GLfloat>& vertices,
        const ScreenPoint& a,
        const ScreenPoint& b,
        const ScreenPoint& c,
        const ScreenPoint& d
) {
    appendTriangle(vertices, a, b, c);
    appendTriangle(vertices, a, c, d);
}

void appendVerticalBillboardRect(
        std::vector<GLfloat>& vertices,
        double centerLongitude,
        double centerLatitude,
        double halfWidthMeters,
        double bottomMeters,
        double topMeters,
        const mbgl::style::CustomLayerRenderParameters& params
) {
    constexpr double tileSize = 512.0;
    const double worldSize = tileSize * std::pow(2.0, params.zoom);
    const double halfWidthNdc =
            halfWidthMeters * metersToMercatorUnits(1.0, centerLatitude) * worldSize * 2.0 /
            params.width;
    const ScreenPoint bottomCenter = projectToNdc(
            centerLongitude,
            centerLatitude,
            bottomMeters,
            params
    );
    const ScreenPoint topCenter = projectToNdc(
            centerLongitude,
            centerLatitude,
            topMeters,
            params
    );
    const ScreenPoint bottomLeft{.x = bottomCenter.x - halfWidthNdc, .y = bottomCenter.y};
    const ScreenPoint bottomRight{.x = bottomCenter.x + halfWidthNdc, .y = bottomCenter.y};
    const ScreenPoint topRight{.x = topCenter.x + halfWidthNdc, .y = topCenter.y};
    const ScreenPoint topLeft{.x = topCenter.x - halfWidthNdc, .y = topCenter.y};
    appendQuad(vertices, bottomLeft, bottomRight, topRight, topLeft);
}

std::vector<GLfloat> buildExclamationMark(
        const mbgl::style::CustomLayerRenderParameters& params
) {
    std::vector<GLfloat> vertices;
    vertices.reserve(12 * 3);
    appendVerticalBillboardRect(
            vertices,
            kCircleLongitude,
            kCircleLatitude,
            kStemWidthMeters / 2.0,
            kDotSizeMeters + kDotGapMeters,
            kDotSizeMeters + kDotGapMeters + kStemHeightMeters,
            params
    );
    appendVerticalBillboardRect(
            vertices,
            kCircleLongitude,
            kCircleLatitude,
            kDotSizeMeters / 2.0,
            0.0,
            kDotSizeMeters,
            params
    );
    return vertices;
}

} // namespace

namespace custom_map_layers::layers::exclamation {

void ExclamationLayer::initialize() {
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
}

void ExclamationLayer::render(const mbgl::style::CustomLayerRenderParameters& params) {
    if (program_.handle() == 0 || vertexBuffer_.handle() == 0) {
        return;
    }

    if (!didLogFirstRender_) {
        __android_log_print(
                ANDROID_LOG_INFO,
                LOG_TAG,
                "render %.0fx%.0f camera=(%.7f, %.7f) zoom=%.2f bearing=%.4f pitch=%.4f marker=(%.7f, %.7f)",
                params.width,
                params.height,
                params.latitude,
                params.longitude,
                params.zoom,
                params.bearing,
                params.pitch,
                kCircleLatitude,
                kCircleLongitude
        );
        didLogFirstRender_ = true;
    }

    vertices_ = buildExclamationMark(params);
    vertexCount_ = static_cast<GLsizei>(vertices_.size() / 3);

    glUseProgram(program_.handle());
    vertexBuffer_.upload(vertices_);
    vertexBuffer_.bind();
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 3 * sizeof(GLfloat), nullptr);
    glDisable(GL_STENCIL_TEST);
    glDisable(GL_DEPTH_TEST);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glDrawArrays(GL_TRIANGLES, 0, vertexCount_);
    glDisableVertexAttribArray(0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glUseProgram(0);
    custom_map_layers::rendering::logGlErrors("render", LOG_TAG);
}

void ExclamationLayer::contextLost() {
    __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "contextLost");
    program_.forget();
    vertexBuffer_.forget();
    resetState();
}

void ExclamationLayer::deinitialize() {
    vertexBuffer_.reset();
    program_.reset();
    custom_map_layers::rendering::logGlErrors("deinitialize", LOG_TAG);
    resetState();
}

void ExclamationLayer::resetState() {
    vertexCount_ = 0;
    didLogFirstRender_ = false;
}

} // namespace custom_map_layers::layers::exclamation
