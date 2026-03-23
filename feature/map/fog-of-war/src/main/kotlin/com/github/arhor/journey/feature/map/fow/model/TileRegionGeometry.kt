package com.github.arhor.journey.feature.map.fow.model

import com.github.arhor.journey.feature.map.fow.toPosition
import org.maplibre.spatialk.geojson.Polygon

internal data class TileRegionGeometry(
    val zoom: Int,
    val outerRing: TileRegionRing,
    val holeRings: List<TileRegionRing> = emptyList(),
) {
    fun toPolygon(): Polygon = Polygon(
        coordinates = listOf(
            outerRing.points.map { it.toPosition(zoom) },
        ) + holeRings.map { ring ->
            ring.points.map { it.toPosition(zoom) }
        },
    )
}
