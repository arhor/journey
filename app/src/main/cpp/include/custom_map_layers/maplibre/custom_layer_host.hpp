#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunknown-pragmas"
#pragma ide diagnostic ignored "OCUnusedGlobalDeclarationInspection"
#pragma once

#include <array>
#include <cstddef>
#include <type_traits>

namespace mbgl::style {

// ABI compatibility mirror for MapLibre Native custom layer host.
//
// Keep this namespace, method order, method signatures, and struct layout in sync
// with the MapLibre Native custom layer ABI. Some methods may appear unused to
// static analysis because they are called indirectly by the native map renderer.
struct CustomLayerRenderParameters {
    double width;
    double height;
    double latitude;
    double longitude;
    double zoom;
    double bearing;
    double pitch;
    double fieldOfView;
    std::array<double, 16> projectionMatrix;
};

class CustomLayerHost {
public:
    virtual ~CustomLayerHost() = default;

    virtual void initialize() = 0;
    virtual void render(const CustomLayerRenderParameters&) = 0;
    virtual void contextLost() = 0;
    virtual void deinitialize() = 0;
};

}  // namespace mbgl::style

#pragma clang diagnostic pop
