package com.github.arhor.journey.feature.map.viewinterop

import com.github.arhor.journey.domain.model.GeoBounds
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.abs

private const val CAMERA_SETTLE_BOUNDS_THRESHOLD = 0.0001

internal class NativeMapViewportReporter(
    private val onViewportChanged: (GeoBounds) -> Unit,
) {
    private var map: MapLibreMap? = null
    private var lastReportedBounds: GeoBounds? = null

    private val cameraIdleListener = MapLibreMap.OnCameraIdleListener {
        reportCurrentViewport()
    }

    fun attach(map: MapLibreMap) {
        cleanup()
        this.map = map
        map.addOnCameraIdleListener(cameraIdleListener)
    }

    fun cleanup() {
        map?.removeOnCameraIdleListener(cameraIdleListener)
        map = null
        lastReportedBounds = null
    }

    fun reportCurrentViewport() {
        val visibleBounds = map
            ?.projection
            ?.visibleRegion
            ?.latLngBounds
            ?.toGeoBounds()
            ?: return

        if (lastReportedBounds?.let { areGeoBoundsEquivalent(it, visibleBounds) } == true) {
            return
        }

        lastReportedBounds = visibleBounds
        onViewportChanged(visibleBounds)
    }
}

private fun LatLngBounds.toGeoBounds(): GeoBounds =
    GeoBounds(
        south = latitudeSouth,
        west = longitudeWest,
        north = latitudeNorth,
        east = longitudeEast,
    )

private fun areGeoBoundsEquivalent(a: GeoBounds, b: GeoBounds): Boolean {
    return abs(a.south - b.south) < CAMERA_SETTLE_BOUNDS_THRESHOLD
        && abs(a.west - b.west) < CAMERA_SETTLE_BOUNDS_THRESHOLD
        && abs(a.north - b.north) < CAMERA_SETTLE_BOUNDS_THRESHOLD
        && abs(a.east - b.east) < CAMERA_SETTLE_BOUNDS_THRESHOLD
}
