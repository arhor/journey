#include "WebMercator.hpp"

#include <cmath>

namespace custom_map_layers::geo {
namespace {

constexpr double kEarthCircumferenceMeters = 40075016.68557849;
constexpr double kTileSize = 512.0;
constexpr double kPi = 3.14159265358979323846264338327950288;
constexpr double kFullCircleRadians = 2.0 * kPi;
constexpr double kDegreesToRadians = kPi / 180.0;

double degreesToRadiansIfNeeded(double value) {
    return std::abs(value) > kFullCircleRadians ? value * kDegreesToRadians : value;
}

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

ScreenPoint projectToNdc(
        double longitude,
        double latitude,
        double altitudeMeters,
        const mbgl::style::CustomLayerRenderParameters& params
) {
    const double worldSize = kTileSize * std::pow(2.0, params.zoom);

    const double cameraX = longitudeToMercatorX(params.longitude) * worldSize;
    const double cameraY = latitudeToMercatorY(params.latitude) * worldSize;

    const double pointX = longitudeToMercatorX(longitude) * worldSize;
    const double pointY = latitudeToMercatorY(latitude) * worldSize;

    const double altitudePixels = altitudeMeters * metersToMercatorUnits(1.0, latitude) * worldSize;

    const double bearingRadians = degreesToRadiansIfNeeded(params.bearing);
    const double pitchRadians = degreesToRadiansIfNeeded(params.pitch);

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

ScreenPoint projectMetersOffsetToNdc(
        double originLongitude,
        double originLatitude,
        double originAltitudeMeters,
        LocalMeters offsetMeters,
        const mbgl::style::CustomLayerRenderParameters& params
) {
    const double worldSize = kTileSize * std::pow(2.0, params.zoom);
    const double worldUnitsPerMeter = metersToMercatorUnits(1.0, originLatitude) * worldSize;

    const double cameraX = longitudeToMercatorX(params.longitude) * worldSize;
    const double cameraY = latitudeToMercatorY(params.latitude) * worldSize;

    const double originX = longitudeToMercatorX(originLongitude) * worldSize;
    const double originY = latitudeToMercatorY(originLatitude) * worldSize;

    const double pointX = originX + offsetMeters.east * worldUnitsPerMeter;
    const double pointY = originY - offsetMeters.north * worldUnitsPerMeter;
    const double pointAltitudePixels = (originAltitudeMeters + offsetMeters.up) * worldUnitsPerMeter;

    const double bearingRadians = degreesToRadiansIfNeeded(params.bearing);
    const double pitchRadians = degreesToRadiansIfNeeded(params.pitch);

    const double dx = pointX - cameraX;
    const double dy = pointY - cameraY;

    const double rotatedX = dx * std::cos(bearingRadians) - dy * std::sin(bearingRadians);
    const double rotatedY = dx * std::sin(bearingRadians) + dy * std::cos(bearingRadians);

    const double pitchedY = rotatedY * std::cos(pitchRadians) - pointAltitudePixels * std::sin(pitchRadians);

    return ScreenPoint{
            .x = 2.0 * rotatedX / params.width,
            .y = -2.0 * pitchedY / params.height,
    };
}

}  // namespace custom_map_layers::geo
