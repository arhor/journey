package com.github.arhor.journey.feature.map.viewinterop

import android.graphics.PointF
import android.location.Location
import androidx.compose.ui.geometry.Offset
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import kotlin.math.abs

private const val SCREEN_POINT_EMIT_THRESHOLD_PX = 0.5f

internal class NativeCurrentLocationScreenPointReporter(
    private val onOriginChanged: (Offset) -> Unit,
) {
    private var map: MapLibreMap? = null
    private var mapView: MapView? = null
    private var lastReportedOrigin: Offset? = null

    private val cameraMoveListener = MapLibreMap.OnCameraMoveListener {
        reportCurrentOrigin()
    }

    fun attach(mapView: MapView, map: MapLibreMap) {
        cleanup()
        this.mapView = mapView
        this.map = map
        map.addOnCameraMoveListener(cameraMoveListener)
    }

    fun cleanup() {
        map?.removeOnCameraMoveListener(cameraMoveListener)
        map = null
        mapView = null
        lastReportedOrigin = null
    }

    fun onLocationUpdated(location: Location) {
        map?.locationComponent?.forceLocationUpdate(location)
        reportCurrentOrigin()
    }

    fun reportCurrentOrigin() {
        val currentMap = map ?: return
        val origin = currentMap.currentLocationScreenPoint()?.toOffset()
            ?: lastReportedOrigin
            ?: mapView?.fallbackCenterOffset()
            ?: return

        if (isEquivalent(origin, lastReportedOrigin)) {
            return
        }

        lastReportedOrigin = origin
        onOriginChanged(origin)
    }
}

private fun MapLibreMap.currentLocationScreenPoint(): PointF? {
    val location = locationComponent.lastKnownLocation ?: return null
    return projection.toScreenLocation(LatLng(location.latitude, location.longitude))
}

private fun PointF.toOffset(): Offset = Offset(x = x, y = y)

private fun MapView.fallbackCenterOffset(): Offset {
    return Offset(x = width / 2f, y = height / 2f)
}

private fun isEquivalent(a: Offset, b: Offset?): Boolean {
    if (b == null) return false

    return abs(a.x - b.x) < SCREEN_POINT_EMIT_THRESHOLD_PX &&
        abs(a.y - b.y) < SCREEN_POINT_EMIT_THRESHOLD_PX
}
