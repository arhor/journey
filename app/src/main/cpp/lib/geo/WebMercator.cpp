#include "WebMercator.hpp"

#include <cmath>

namespace custom_map_layers::geo {
namespace {

constexpr double kEarthCircumferenceMeters = 40075016.68557849;
constexpr double tileSize = 512.0;

}

double longitudeToMercatorX(double longitude) {
    return (longitude + 180.0) / 360.0;
}

double latitudeToMercatorY(double latitude) {
    const double radians = latitude * M_PI / 180.0;
    return (1.0 - std::log(std::tan(radians) + (1.0 / std::cos(radians))) / M_PI) / 2.0;
}

double metersToMercatorUnits(double meters, double latitude) {
    const double radians = latitude * M_PI / 180.0;
    return meters / (kEarthCircumferenceMeters * std::cos(radians));
}

ScreenPoint projectToNdc(
        double longitude,
        double latitude,
        double altitudeMeters,
        const mbgl::style::CustomLayerRenderParameters& params
) {
    const double worldSize = tileSize * std::pow(2.0, params.zoom);
    const double cameraX = longitudeToMercatorX(params.longitude) * worldSize;
    const double cameraY = latitudeToMercatorY(params.latitude) * worldSize;
    const double pointX = longitudeToMercatorX(longitude) * worldSize;
    const double pointY = latitudeToMercatorY(latitude) * worldSize;
    const double altitudePixels = altitudeMeters * metersToMercatorUnits(1.0, latitude) * worldSize;
    const double bearingRadians = std::abs(params.bearing) > 2.0 * M_PI
                                  ? -params.bearing * M_PI / 180.0
                                  : -params.bearing;
    const double pitchRadians = std::abs(params.pitch) > 2.0 * M_PI
                                ? params.pitch * M_PI / 180.0
                                : params.pitch;
    const double dx = pointX - cameraX;
    const double dy = pointY - cameraY;
    const double rotatedX = dx * std::cos(bearingRadians) - dy * std::sin(bearingRadians);
    const double rotatedY = dx * std::sin(bearingRadians) + dy * std::cos(bearingRadians);
    const double pitchedY = rotatedY * std::cos(pitchRadians) - altitudePixels * std::sin(pitchRadians);

    return ScreenPoint{
            .x = 2.0 * rotatedX / params.width,
            .y = -2.0 * pitchedY / params.height,
    };
}

} // namespace custom_map_layers::geo
