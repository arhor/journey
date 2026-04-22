package com.github.arhor.journey.feature.map.viewinterop

import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.feature.map.camera.areGeoBoundsEquivalent
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap

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
