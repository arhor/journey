package com.github.arhor.journey.feature.map.model

import androidx.compose.runtime.Immutable

@Immutable
data class FogOfWarDiagnostics(
    val lastPreparation: FogOfWarPreparationMetrics = FogOfWarPreparationMetrics(),
    val cache: FogOfWarCacheMetrics = FogOfWarCacheMetrics(),
    val sourceUpdate: FogOfWarSourceUpdateMetrics = FogOfWarSourceUpdateMetrics(),
    val prepareCancellationCount: Long = 0,
)

@Immutable
data class FogOfWarPreparationMetrics(
    val totalPrepareMillis: Long = 0,
    val calculateFogRangesMillis: Long = 0,
    val buildRenderDataMillis: Long = 0,
    val geometryBuildMillis: Long = 0,
    val featureCollectionBuildMillis: Long = 0,
    val visibleTileCount: Long = 0,
    val bufferedTileCount: Long = 0,
    val exploredTileCount: Int = 0,
    val fogRangeCount: Int = 0,
    val expandedFogCellCount: Long = 0,
    val connectedRegionCount: Int = 0,
    val boundaryEdgeCount: Int = 0,
    val loopCount: Int = 0,
    val featureCount: Int = 0,
    val ringPointCount: Int = 0,
    val renderCacheHit: Boolean = false,
)

@Immutable
data class FogOfWarCacheMetrics(
    val renderHits: Long = 0,
    val renderMisses: Long = 0,
    val fullRangeHits: Long = 0,
    val fullRangeMisses: Long = 0,
)

@Immutable
data class FogOfWarSourceUpdateMetrics(
    val updateCount: Long = 0,
    val lastSetDataMillis: Long = 0,
)
