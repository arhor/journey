#pragma once

#include <numbers>
#include <string>

namespace custom_map_layers::layers::model {

struct ModelInstance {
    std::string assetPath;
    double latitude = 0.0;
    double longitude = 0.0;
    double altitudeMeters = 0.0;
    double scaleMetersPerModelUnit = 1.0;
    double headingRadians = 0.0;
};

inline double degreesToRadians(double degrees) {
    return degrees * std::numbers::pi / 180.0;
}

}  // namespace custom_map_layers::layers::model
