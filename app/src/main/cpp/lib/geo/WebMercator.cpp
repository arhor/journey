#include "WebMercator.hpp"

#include <cmath>
#include <numbers>

namespace custom_map_layers::geo {
namespace {

constexpr double kEarthCircumferenceMeters = 40075016.68557849;
constexpr double kDegreesToRadians = std::numbers::pi / 180.0;

}  // namespace

double longitudeToMercatorX(double longitude) {
    return (longitude + 180.0) / 360.0;
}

double latitudeToMercatorY(double latitude) {
    const double radians = latitude * kDegreesToRadians;
    return (1.0 - std::log(std::tan(radians) + (1.0 / std::cos(radians))) / std::numbers::pi) / 2.0;
}

double metersToMercatorUnits(double meters, double latitude) {
    const double radians = latitude * kDegreesToRadians;
    return meters / (kEarthCircumferenceMeters * std::cos(radians));
}

}  // namespace custom_map_layers::geo
