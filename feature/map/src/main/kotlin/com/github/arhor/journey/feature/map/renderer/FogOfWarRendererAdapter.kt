package com.github.arhor.journey.feature.map.renderer

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.feature.map.FogOfWarBandUiState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Polygon

@Composable
@MaplibreComposable
fun FogOfWarRendererAdapter(
    fogBands: List<FogOfWarBandUiState>,
) {
    fogBands.forEach { band ->
        FogOfWarBandRenderer(band)
    }
}

@Composable
@MaplibreComposable
private fun FogOfWarBandRenderer(
    band: FogOfWarBandUiState,
) {
    val geoJsonData = remember(band.opacity, band.ranges) {
        band.ranges.toGeoJsonDataOrNull(
            opacity = band.opacity,
        )
    } ?: return
    val idSuffix = remember(band.opacity) { band.opacity.toBits().toString(16) }
    val source = remember(idSuffix) {
        GeoJsonSource(
            id = "$FOG_OF_WAR_SOURCE_ID_PREFIX-$idSuffix",
            data = geoJsonData,
            options = org.maplibre.compose.sources.GeoJsonOptions(),
        )
    }

    LaunchedEffect(source, geoJsonData) {
        source.setData(geoJsonData)
    }

    FillLayer(
        id = "$FOG_OF_WAR_LAYER_ID_PREFIX-$idSuffix",
        source = source,
        color = const(Color(0xFF000000)),
        opacity = const(band.opacity),
    )
}

internal fun List<ExplorationTileRange>.toFeatureCollection(): FeatureCollection<Polygon, JsonObject?> =
    FeatureCollection(
        features = toTileRegionGeometries().mapIndexed { index, region ->
            Feature(
                geometry = region.toPolygon(),
                properties = buildJsonObject { },
                id = JsonPrimitive("${region.zoom}#$index"),
            )
        },
    )

internal fun List<ExplorationTileRange>.toGeoJsonDataOrNull(
    opacity: Float? = null,
): GeoJsonData.Features? {
    if (isEmpty()) {
        return null
    }

    return toFeatureCollectionOrRectangularFallback(opacity = opacity)
        .let(GeoJsonData::Features)
}

internal fun List<ExplorationTileRange>.toFeatureCollectionOrRectangularFallback(
    opacity: Float? = null,
): FeatureCollection<Polygon, JsonObject?> = runCatching { toFeatureCollection() }
        .onFailure { throwable ->
            logFogOfWarError(
                tag = FOG_OF_WAR_RENDERER_TAG,
                message = buildFogBandDiagnostics(
                    opacity = opacity,
                    fallback = "rectangular tile polygons",
                ),
                throwable = throwable,
            )
        }
        .recoverCatching { toRectangularFeatureCollection() }
        .getOrThrow()
 

private fun List<ExplorationTileRange>.buildFogBandDiagnostics(
    opacity: Float?,
    fallback: String,
    maxRanges: Int = 8,
): String {
    val firstRange = first()
    val zoom = firstRange.zoom
    val minX = minOf(ExplorationTileRange::minX)
    val maxX = maxOf(ExplorationTileRange::maxX)
    val minY = minOf(ExplorationTileRange::minY)
    val maxY = maxOf(ExplorationTileRange::maxY)
    val totalTileCount = sumOf { range ->
        (range.maxX - range.minX + 1) * (range.maxY - range.minY + 1)
    }
    val rangesPreview = asSequence()
        .sortedWith(
            compareBy<ExplorationTileRange> { it.minY }
                .thenBy { it.minX }
                .thenBy { it.maxY }
                .thenBy { it.maxX },
        )
        .take(maxRanges)
        .joinToString(separator = "; ") { range ->
            "x=${range.minX}..${range.maxX},y=${range.minY}..${range.maxY}"
        }

    return buildString {
        append("Fog band geometry generation failed")
        append(". opacity=")
        append(opacity)
        append(", zoom=")
        append(zoom)
        append(", rangeCount=")
        append(size)
        append(", totalTileCount=")
        append(totalTileCount)
        append(", bounds=x=")
        append(minX)
        append("..")
        append(maxX)
        append(",y=")
        append(minY)
        append("..")
        append(maxY)
        append(", rangesPreview=")
        append(rangesPreview)
        append(", fallback=")
        append(fallback)

        if (size > maxRanges) {
            append(" ...")
        }
    }
}

private fun List<ExplorationTileRange>.toRectangularFeatureCollection(): FeatureCollection<Polygon, JsonObject?> =
    FeatureCollection(
        features = sortedWith(
            compareBy<ExplorationTileRange> { it.zoom }
                .thenBy { it.minY }
                .thenBy { it.minX },
        ).mapIndexed { index, range ->
            Feature(
                geometry = range.toRectangularTileRegionGeometry().toPolygon(),
                properties = buildJsonObject { },
                id = JsonPrimitive("${range.zoom}#fallback#$index"),
            )
        },
    )

private fun ExplorationTileRange.toRectangularTileRegionGeometry(): TileRegionGeometry = TileRegionGeometry(
    zoom = zoom,
    outerRing = TileRegionRing(
        listOf(
            GridPoint(x = minX.toDouble(), y = minY.toDouble()),
            GridPoint(x = (maxX + 1).toDouble(), y = minY.toDouble()),
            GridPoint(x = (maxX + 1).toDouble(), y = (maxY + 1).toDouble()),
            GridPoint(x = minX.toDouble(), y = (maxY + 1).toDouble()),
            GridPoint(x = minX.toDouble(), y = minY.toDouble()),
        ),
    ),
)

internal const val FOG_OF_WAR_SOURCE_ID_PREFIX = "fog-of-war-source"
internal const val FOG_OF_WAR_LAYER_ID_PREFIX = "fog-of-war-layer"
private const val FOG_OF_WAR_RENDERER_TAG = "FogOfWarRenderer"

private fun logFogOfWarError(
    tag: String,
    message: String,
    throwable: Throwable? = null,
) {
    val logged = runCatching {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }.isSuccess

    if (logged) {
        return
    }

    System.err.println("$tag: $message")
    throwable?.printStackTrace(System.err)
}
