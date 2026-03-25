package com.github.arhor.journey.feature.map.fow

import com.github.arhor.journey.domain.model.ExplorationTileGrid
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.feature.map.fow.model.FogBufferRegion
import com.github.arhor.journey.feature.map.fow.model.FogViewportSnapshot
import kotlin.math.ceil
import kotlin.math.sqrt

// "Screens" here means total area coverage relative to the current viewport area,
// not padding on a single edge. 5.0 and 10.0 intentionally bias toward earlier
// recompute and larger prepared fog coverage so fast dragging is less likely to
// expose leading-edge seams.
private const val TARGET_TRIGGER_AREA_IN_SCREENS = 5.0
private const val TARGET_BUFFERED_AREA_IN_SCREENS = 10.0
private const val MIN_TILE_PADDING = 1
private const val MIN_BUFFERED_PADDING_DELTA = 2
private val TRIGGER_TILE_PADDING_MULTIPLIER = tilePaddingMultiplierForTargetArea(TARGET_TRIGGER_AREA_IN_SCREENS)
private val BUFFERED_TILE_PADDING_MULTIPLIER = tilePaddingMultiplierForTargetArea(TARGET_BUFFERED_AREA_IN_SCREENS)

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

// Treat touching the trigger edge as a handoff point so the next buffer starts early.
internal fun FogBufferRegion.shouldRecompute(visibleBounds: GeoBounds): Boolean =
    !triggerBounds.strictlyContains(visibleBounds)


internal fun GeoBounds.strictlyContains(other: GeoBounds): Boolean {
    return other.south > south
        && other.west > west
        && other.north < north
        && other.east < east
}

private fun ExplorationTileRange.widthInTiles(): Int = maxX - minX + 1

private fun ExplorationTileRange.heightInTiles(): Int = maxY - minY + 1

private fun bufferedTilePaddingFor(
    visibleSpan: Int,
    triggerPadding: Int,
): Int = maxOf(
    triggerPadding + MIN_BUFFERED_PADDING_DELTA,
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

private fun tilePaddingMultiplierForTargetArea(
    targetAreaInScreens: Double,
): Double = (sqrt(targetAreaInScreens) - 1.0) / 2.0
