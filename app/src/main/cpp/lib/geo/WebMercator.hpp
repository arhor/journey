#pragma once

#include "custom_map_layers/maplibre/custom_layer_host.hpp"

namespace custom_map_layers::geo {

struct ScreenPoint {
    double x;
    double y;
};

struct LocalMeters {
    double east;
    double north;
    double up;
};

double longitudeToMercatorX(double longitude);
double latitudeToMercatorY(double latitude);
double metersToMercatorUnits(double meters, double latitude);

ScreenPoint projectToNdc(
        double longitude,
        double latitude,
        double altitudeMeters,
        const mbgl::style::CustomLayerRenderParameters& parameters
);

ScreenPoint projectMetersOffsetToNdc(
        double originLongitude,
        double originLatitude,
        double originAltitudeMeters,
        LocalMeters offsetMeters,
        const mbgl::style::CustomLayerRenderParameters& parameters
);

}  // namespace custom_map_layers::geo
