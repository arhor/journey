#include "WebMercator.hpp"

#include <cmath>

namespace custom_map_layers::geo {
namespace {

constexpr double kEarthCircumferenceMeters = 40075016.68557849;
constexpr double kPi = 3.14159265358979323846264338327950288;
constexpr double kDegreesToRadians = kPi / 180.0;

}  // namespace

double longitudeToMercatorX(double longitude) {
    return (longitude + 180.0) / 360.0;
}

double latitudeToMercatorY(double latitude) {
    const double radians = latitude * kDegreesToRadians;
    return (1.0 - std::log(std::tan(radians) + (1.0 / std::cos(radians))) / kPi) / 2.0;
}

double metersToMercatorUnits(double meters, double latitude) {
    const double radians = latitude * kDegreesToRadians;
    return meters / (kEarthCircumferenceMeters * std::cos(radians));
}

}  // namespace custom_map_layers::geo
