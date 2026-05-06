#pragma once

#include <array>

namespace mbgl::style {

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

    virtual void render(const CustomLayerRenderParameters& parameters) = 0;

    virtual void contextLost() = 0;

    virtual void deinitialize() = 0;
};

} // namespace mbgl::style
