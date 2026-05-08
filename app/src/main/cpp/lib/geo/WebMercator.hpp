#pragma once

namespace custom_map_layers::geo {

struct LocalMeters {
    double east;
    double north;
    double up;
};

double longitudeToMercatorX(double longitude);
double latitudeToMercatorY(double latitude);
double metersToMercatorUnits(double meters, double latitude);

}  // namespace custom_map_layers::geo
