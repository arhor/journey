package com.github.arhor.journey.feature.map.prewarm

import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapViewportSize
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan
import javax.inject.Inject

class MapTileCoverageCalculator @Inject constructor() {
    internal fun calculate(
        request: MapTilePrewarmRequest,
        sources: List<TileSourceDefinition>,
        pixelRatio: Float,
    ): List<String> {
        if (request.burstLimit <= 0) {
            return emptyList()
        }

        val samples = sampleViewports(request)
        if (samples.isEmpty()) {
            return emptyList()
        }

        val ratioSuffix = if (pixelRatio >= RETINA_PIXEL_RATIO_THRESHOLD) "@2x" else ""
        val urls = linkedSetOf<String>()

        sources.forEach { source ->
            samples.forEach sampleLoop@ { sample ->
                val boundedViewport = source.bounds?.intersect(sample.bounds)
                    ?: if (source.bounds != null) {
                        return@sampleLoop
                    } else {
                        sample.bounds
                    }
                val zooms = sample.zoom.resolveTileZooms(
                    minZoom = source.minZoom,
                    maxZoom = source.maxZoom,
                )
                zooms.forEach { zoom ->
                    if (urls.size >= request.burstLimit) {
                        return urls.toList()
                    }
                    urls += tileUrlsForBounds(
                        source = source,
                        bounds = boundedViewport,
                        zoom = zoom,
                        ratioSuffix = ratioSuffix,
                        remainingCapacity = request.burstLimit - urls.size,
                    )
                }
            }
        }

        return urls.toList()
    }

    internal fun sampleViewports(
        request: MapTilePrewarmRequest,
    ): List<SampledViewport> {
        val currentCamera = request.currentCamera ?: request.targetCamera
        val currentBounds = request.currentVisibleBounds
            ?: approximateBounds(currentCamera, request.viewportSize)
            ?: return emptyList()

        val fractions = buildSampleFractions(
            sampleCount = request.sampleCount,
            includeIntermediateSamples = request.animationDuration.isPositive(),
        )

        return fractions.map { fraction ->
            val sampledCamera = interpolateCamera(
                from = currentCamera,
                to = request.targetCamera,
                fraction = fraction,
            )
            val sampledBounds = if (request.currentVisibleBounds != null) {
                transformBounds(
                    baseBounds = currentBounds,
                    baseCamera = currentCamera,
                    targetCamera = sampledCamera,
                )
            } else {
                approximateBounds(sampledCamera, request.viewportSize) ?: currentBounds
            }

            SampledViewport(
                zoom = sampledCamera.zoom,
                bounds = sampledBounds,
            )
        }
    }

    private fun tileUrlsForBounds(
        source: TileSourceDefinition,
        bounds: GeoBounds,
        zoom: Int,
        ratioSuffix: String,
        remainingCapacity: Int,
    ): List<String> {
        if (remainingCapacity <= 0) {
            return emptyList()
        }

        val xRange = longitudeRangeToTileRange(bounds.west, bounds.east, zoom)
        val yRange = latitudeRangeToTileRange(bounds.south, bounds.north, zoom)
        val urls = ArrayList<String>(remainingCapacity)

        loop@ for (y in yRange.first..yRange.last) {
            for (x in xRange.first..xRange.last) {
                source.tileTemplates.forEach { template ->
                    urls += expandTileTemplate(
                        template = template,
                        x = x,
                        y = y,
                        z = zoom,
                        scheme = source.scheme,
                        ratioSuffix = ratioSuffix,
                    )
                    if (urls.size >= remainingCapacity) {
                        break@loop
                    }
                }
            }
        }

        return urls
    }

    private fun expandTileTemplate(
        template: String,
        x: Int,
        y: Int,
        z: Int,
        scheme: TileScheme,
        ratioSuffix: String,
    ): String {
        val maxIndex = (1 shl z) - 1
        val schemeY = if (scheme == TileScheme.TMS) {
            maxIndex - y
        } else {
            y
        }
        val invertedY = maxIndex - y

        return template
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{y}", schemeY.toString())
            .replace("{-y}", invertedY.toString())
            .replace("{ratio}", ratioSuffix)
    }

    private fun longitudeRangeToTileRange(
        west: Double,
        east: Double,
        zoom: Int,
    ): IntRange {
        val tileCount = 1 shl zoom
        val minX = floor(longitudeToTileX(west, zoom)).toInt()
            .coerceIn(0, tileCount - 1)
        val maxX = floor(longitudeToTileX(east, zoom)).toInt()
            .coerceIn(0, tileCount - 1)
        return minX..maxOf(minX, maxX)
    }

    private fun latitudeRangeToTileRange(
        south: Double,
        north: Double,
        zoom: Int,
    ): IntRange {
        val tileCount = 1 shl zoom
        val minY = floor(latitudeToTileY(north, zoom)).toInt()
            .coerceIn(0, tileCount - 1)
        val maxY = floor(latitudeToTileY(south, zoom)).toInt()
            .coerceIn(0, tileCount - 1)
        return minY..maxOf(minY, maxY)
    }

    private fun interpolateCamera(
        from: CameraPositionState,
        to: CameraPositionState,
        fraction: Double,
    ): CameraPositionState = CameraPositionState(
        target = from.target.lerp(to.target, fraction),
        zoom = from.zoom + (to.zoom - from.zoom) * fraction,
        bearing = from.bearing + (to.bearing - from.bearing) * fraction,
    )

    private fun transformBounds(
        baseBounds: GeoBounds,
        baseCamera: CameraPositionState,
        targetCamera: CameraPositionState,
    ): GeoBounds {
        val baseCenter = baseCamera.target.toMercatorCoordinate()
        val targetCenter = targetCamera.target.toMercatorCoordinate()
        val zoomScale = 2.0.pow(baseCamera.zoom - targetCamera.zoom)
        val westX = longitudeToMercatorX(baseBounds.west)
        val eastX = longitudeToMercatorX(baseBounds.east)
        val northY = latitudeToMercatorY(baseBounds.north)
        val southY = latitudeToMercatorY(baseBounds.south)

        val spanX = (eastX - westX) * zoomScale
        val spanY = (southY - northY) * zoomScale

        val sampleWest = (targetCenter.x - spanX / 2.0).coerceIn(0.0, 1.0)
        val sampleEast = (targetCenter.x + spanX / 2.0).coerceIn(0.0, 1.0)
        val sampleNorth = (targetCenter.y - spanY / 2.0).coerceIn(0.0, 1.0)
        val sampleSouth = (targetCenter.y + spanY / 2.0).coerceIn(0.0, 1.0)

        return GeoBounds(
            south = mercatorYToLatitude(sampleSouth),
            west = mercatorXToLongitude(sampleWest),
            north = mercatorYToLatitude(sampleNorth),
            east = mercatorXToLongitude(sampleEast),
        )
    }

    private fun approximateBounds(
        camera: CameraPositionState,
        viewportSize: MapViewportSize?,
    ): GeoBounds? {
        val viewport = viewportSize ?: return null
        if (viewport.widthPx <= 0 || viewport.heightPx <= 0) {
            return null
        }

        val center = camera.target.toMercatorCoordinate()
        val worldSize = TILE_EXTENT * 2.0.pow(camera.zoom)
        val halfSpanX = viewport.widthPx / (2.0 * worldSize)
        val halfSpanY = viewport.heightPx / (2.0 * worldSize)

        return GeoBounds(
            south = mercatorYToLatitude((center.y + halfSpanY).coerceIn(0.0, 1.0)),
            west = mercatorXToLongitude((center.x - halfSpanX).coerceIn(0.0, 1.0)),
            north = mercatorYToLatitude((center.y - halfSpanY).coerceIn(0.0, 1.0)),
            east = mercatorXToLongitude((center.x + halfSpanX).coerceIn(0.0, 1.0)),
        )
    }

    private fun buildSampleFractions(
        sampleCount: Int,
        includeIntermediateSamples: Boolean,
    ): List<Double> {
        if (!includeIntermediateSamples || sampleCount <= 1) {
            return listOf(1.0)
        }

        val fractions = ArrayList<Double>(sampleCount)
        for (index in sampleCount downTo 1) {
            fractions += index.toDouble() / sampleCount.toDouble()
        }
        return fractions
    }

    private fun Double.resolveTileZooms(
        minZoom: Int,
        maxZoom: Int,
    ): List<Int> {
        val zoomFloor = floor(this).toInt().coerceIn(minZoom, maxZoom)
        val zoomCeil = ceil(this).toInt().coerceIn(minZoom, maxZoom)
        return if (zoomFloor == zoomCeil) {
            listOf(zoomFloor)
        } else {
            listOf(zoomCeil, zoomFloor)
        }
    }

    private fun longitudeToTileX(longitude: Double, zoom: Int): Double =
        longitudeToMercatorX(longitude) * (1 shl zoom)

    private fun latitudeToTileY(latitude: Double, zoom: Int): Double =
        latitudeToMercatorY(latitude) * (1 shl zoom)

    private fun longitudeToMercatorX(longitude: Double): Double =
        ((longitude + 180.0) / 360.0).coerceIn(0.0, 1.0)

    private fun latitudeToMercatorY(latitude: Double): Double {
        val clampedLatitude = latitude.coerceIn(MIN_LATITUDE, MAX_LATITUDE)
        val radians = Math.toRadians(clampedLatitude)
        val projected = 0.5 - ln((1 + sin(radians)) / (1 - sin(radians))) / (4.0 * PI)
        return projected.coerceIn(0.0, 1.0)
    }

    private fun mercatorXToLongitude(value: Double): Double =
        value * 360.0 - 180.0

    private fun mercatorYToLatitude(value: Double): Double {
        val exponent = kotlin.math.exp((0.5 - value) * 2.0 * PI)
        return Math.toDegrees(2.0 * kotlin.math.atan(exponent) - PI / 2.0)
            .coerceIn(MIN_LATITUDE, MAX_LATITUDE)
    }

    private fun LatLng.toMercatorCoordinate(): MercatorCoordinate =
        MercatorCoordinate(
            x = longitudeToMercatorX(longitude),
            y = latitudeToMercatorY(latitude),
        )

    private fun LatLng.lerp(
        other: LatLng,
        fraction: Double,
    ): LatLng =
        LatLng(
            latitude = latitude + (other.latitude - latitude) * fraction,
            longitude = longitude + (other.longitude - longitude) * fraction,
        )

    internal data class SampledViewport(
        val zoom: Double,
        val bounds: GeoBounds,
    )

    private data class MercatorCoordinate(
        val x: Double,
        val y: Double,
    )

    private companion object {
        private const val MAX_LATITUDE = 85.05112878
        private const val MIN_LATITUDE = -85.05112878
        private const val RETINA_PIXEL_RATIO_THRESHOLD = 1.5f
        private const val TILE_EXTENT = 512.0
    }
}
