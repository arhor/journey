package com.github.arhor.journey.feature.map.fow

import com.github.arhor.journey.domain.model.ExplorationTileGrid
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.feature.map.fow.model.FogBufferRegion
import com.github.arhor.journey.feature.map.fow.model.FogViewportSnapshot
import kotlin.math.ceil

private const val TRIGGER_TILE_PADDING_MULTIPLIER = 0.5
private const val BUFFERED_TILE_PADDING_MULTIPLIER = 1.0
private const val MIN_TILE_PADDING = 1

internal fun createFogViewportSnapshot(
    visibleBounds: GeoBounds,
    canonicalZoom: Int,
): FogViewportSnapshot {
    val visibleTileRange = ExplorationTileGrid.tileRange(
        bounds = visibleBounds,
        zoom = canonicalZoom,
    )

    return FogViewportSnapshot(
        visibleBounds = visibleBounds,
        visibleTileRange = visibleTileRange,
        visibleTileCount = visibleTileRange.tileCount,
    )
}

internal fun createFogBufferRegion(
    visibleTileRange: ExplorationTileRange,
): FogBufferRegion {
    val visibleTileWidth = visibleTileRange.widthInTiles()
    val visibleTileHeight = visibleTileRange.heightInTiles()
    val triggerHorizontalPadding = tilePaddingFor(
        visibleSpan = visibleTileWidth,
        multiplier = TRIGGER_TILE_PADDING_MULTIPLIER,
    )
    val triggerVerticalPadding = tilePaddingFor(
        visibleSpan = visibleTileHeight,
        multiplier = TRIGGER_TILE_PADDING_MULTIPLIER,
    )
    val bufferedHorizontalPadding = bufferedTilePaddingFor(
        visibleSpan = visibleTileWidth,
        triggerPadding = triggerHorizontalPadding,
    )
    val bufferedVerticalPadding = bufferedTilePaddingFor(
        visibleSpan = visibleTileHeight,
        triggerPadding = triggerVerticalPadding,
    )
    val triggerTileRange = visibleTileRange.expandedBy(
        horizontalTilePadding = triggerHorizontalPadding,
        verticalTilePadding = triggerVerticalPadding,
    )
    val bufferedTileRange = visibleTileRange.expandedBy(
        horizontalTilePadding = bufferedHorizontalPadding,
        verticalTilePadding = bufferedVerticalPadding,
    )

    return FogBufferRegion(
        triggerBounds = ExplorationTileGrid.bounds(triggerTileRange),
        bufferedBounds = ExplorationTileGrid.bounds(bufferedTileRange),
        triggerTileRange = triggerTileRange,
        bufferedTileRange = bufferedTileRange,
    )
}

internal fun createFogBufferRegion(
    visibleBounds: GeoBounds,
    canonicalZoom: Int,
): FogBufferRegion = createFogBufferRegion(
    visibleTileRange = createFogViewportSnapshot(
        visibleBounds = visibleBounds,
        canonicalZoom = canonicalZoom,
    ).visibleTileRange,
)

// Treat touching the trigger edge as a handoff point so the next buffer starts early.
internal fun FogBufferRegion.shouldRecompute(visibleBounds: GeoBounds): Boolean =
    !triggerBounds.strictlyContains(visibleBounds)


internal fun GeoBounds.strictlyContains(other: GeoBounds): Boolean {
    return other.south > south &&
        other.west > west &&
        other.north < north &&
        other.east < east
}

internal fun ExplorationTileRange.contains(other: ExplorationTileRange): Boolean {
    return zoom == other.zoom &&
        other.minX >= minX &&
        other.maxX <= maxX &&
        other.minY >= minY &&
        other.maxY <= maxY
}

internal fun ExplorationTileRange.widthInTiles(): Int = maxX - minX + 1

internal fun ExplorationTileRange.heightInTiles(): Int = maxY - minY + 1

private fun bufferedTilePaddingFor(
    visibleSpan: Int,
    triggerPadding: Int,
): Int = maxOf(
    triggerPadding + 1,
    tilePaddingFor(
        visibleSpan = visibleSpan,
        multiplier = BUFFERED_TILE_PADDING_MULTIPLIER,
    ),
)

private fun tilePaddingFor(
    visibleSpan: Int,
    multiplier: Double,
): Int = ceil(visibleSpan * multiplier).toInt()
    .coerceAtLeast(MIN_TILE_PADDING)
