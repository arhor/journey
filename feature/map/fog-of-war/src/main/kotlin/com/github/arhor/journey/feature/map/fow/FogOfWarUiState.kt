package com.github.arhor.journey.feature.map.fow

import androidx.compose.runtime.Immutable
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.GeoBounds
import kotlin.math.roundToInt

@Immutable
data class FogOfWarUiState(
    val isOverlayEnabled: Boolean = true,
    val canonicalZoom: Int = 0,
    val visibleBounds: GeoBounds? = null,
    val triggerBounds: GeoBounds? = null,
    val bufferedBounds: GeoBounds? = null,
    val visibleTileRange: ExplorationTileRange? = null,
    val fogRanges: List<ExplorationTileRange> = emptyList(),
    val renderData: FogOfWarRenderData? = null,
    val visibleTileCount: Long = 0,
    val exploredVisibleTileCount: Int = 0,
    val isSuppressedByVisibleTileLimit: Boolean = false,
    val isRecomputing: Boolean = false,
    val diagnostics: FogOfWarDiagnostics = FogOfWarDiagnostics(),
)

fun FogOfWarUiState.toSummaryDebugString(): String = buildString {
    append("Fog ranges=")
    append(fogRanges.size)
    append(" features=")
    append(diagnostics.lastPreparation.featureCount)
    append(" cells=")
    append(diagnostics.lastPreparation.expandedFogCellCount)
    append(" explored=")
    append(diagnostics.lastPreparation.exploredTileCount)
    append(" loops=")
    append(diagnostics.lastPreparation.loopCount)
    append(" points=")
    append(diagnostics.lastPreparation.ringPointCount)
}

fun FogOfWarUiState.toBufferingDebugString(): String = buildString {
    append("FOW buffer: loading=")
    append(isRecomputing)
    append(" viewport=")
    append(visibleBounds.toDebugString())
    append(" trigger=")
    append(triggerBounds.toDebugString())
    append(" buffered=")
    append(bufferedBounds.toDebugString())
    append('\n')
    append("prepare(ms): total=")
    append(diagnostics.lastPreparation.totalPrepareMillis)
    append(" calc=")
    append(diagnostics.lastPreparation.calculateFogRangesMillis)
    append(" render=")
    append(diagnostics.lastPreparation.buildRenderDataMillis)
    append(" geom=")
    append(diagnostics.lastPreparation.geometryBuildMillis)
    append(" geojson=")
    append(diagnostics.lastPreparation.featureCollectionBuildMillis)
    append(" setData=")
    append(diagnostics.sourceUpdate.lastSetDataMillis)
    append('\n')
    append("tiles: visible=")
    append(diagnostics.lastPreparation.visibleTileCount)
    append(" buffered=")
    append(diagnostics.lastPreparation.bufferedTileCount)
    append(" regions=")
    append(diagnostics.lastPreparation.connectedRegionCount)
    append(" edges=")
    append(diagnostics.lastPreparation.boundaryEdgeCount)
    append('\n')
    append("cache: hit=")
    append(diagnostics.lastPreparation.renderCacheHit)
    append(" render=")
    append(diagnostics.cache.renderHits)
    append('/')
    append(diagnostics.cache.renderMisses)
    append(" full=")
    append(diagnostics.cache.fullRangeHits)
    append('/')
    append(diagnostics.cache.fullRangeMisses)
    append(" updates=")
    append(diagnostics.sourceUpdate.updateCount)
    append(" cancelled=")
    append(diagnostics.prepareCancellationCount)
}

private fun GeoBounds?.toDebugString(): String {
    return this?.let { bounds ->
        "[${bounds.south.debugCoord()},${bounds.west.debugCoord()} .. ${bounds.north.debugCoord()},${bounds.east.debugCoord()}]"
    } ?: "n/a"
}

private fun Double.debugCoord(): String = (this * 10_000.0).roundToInt()
    .div(10_000.0)
    .toString()
