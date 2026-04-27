package com.github.arhor.journey.feature.map

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val DOT_MAX_ALPHA = 0.98f
private const val GRID_MAX_ALPHA = 0.24f
private const val WAVE_LIFETIME_MS = 4200f
private const val REVEAL_LEAD_MS = 340f
private const val MOTION_DURATION_MS = 300f
private const val FADE_DURATION_MS = 420f
private val RIPPLE_COLOR = Color(0xFF63FF9C)

internal data class RippleLaunchRequest(
    val id: Long,
)

private data class Wave(
    val center: Offset,
    val startedAtMs: Long,
)

private data class GridNode(
    val x: Float,
    val y: Float,
    val radiusJitter: Float,
)

private data class GridLayout(
    val columns: Int,
    val rows: Int,
    val nodes: List<GridNode>,
)

private data class NodeState(
    val displacedPosition: Offset,
    val dotRadius: Float,
    val dotAlpha: Float,
    val gridAlpha: Float,
)

@Composable
internal fun RippleGridOverlay(
    launchRequest: RippleLaunchRequest?,
    waveOrigin: Offset?,
    modifier: Modifier = Modifier,
) {
    val activeWaves = remember { mutableStateListOf<Wave>() }
    var animationTimeMs by remember { mutableLongStateOf(0L) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var lastHandledRequestId by remember { mutableLongStateOf(-1L) }
    val density = LocalDensity.current

    val spacingPx = with(density) { 18.dp.toPx() }
    val baseDotRadius = with(density) { 0.75.dp.toPx() }
    val maxDotGrowth = with(density) { 3.0.dp.toPx() }
    val jumpHeight = with(density) { 25.dp.toPx() }
    val gridLineWidth = with(density) { 0.7.dp.toPx() }
    val waveSpeedPxPerMs = with(density) { 0.22.dp.toPx() }
    val warpStrength = with(density) { 1.4.dp.toPx() }

    val gridLayout = remember(canvasSize, spacingPx) {
        buildGridLayout(canvasSize = canvasSize, spacingPx = spacingPx)
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                animationTimeMs = frameTimeNanos / 1_000_000L
                activeWaves.removeAll { wave ->
                    animationTimeMs - wave.startedAtMs > WAVE_LIFETIME_MS
                }
            }
        }
    }

    LaunchedEffect(launchRequest, canvasSize) {
        if (launchRequest == null || launchRequest.id == lastHandledRequestId) {
            return@LaunchedEffect
        }
        if (canvasSize.width == 0 || canvasSize.height == 0) {
            return@LaunchedEffect
        }

        val center = waveOrigin ?: Offset(
            x = canvasSize.width / 2f,
            y = canvasSize.height / 2f,
        )
        activeWaves += Wave(
            center = center,
            startedAtMs = animationTimeMs,
        )
        lastHandledRequestId = launchRequest.id
    }

    Canvas(
        modifier = modifier.onSizeChanged { canvasSize = it },
    ) {
        if (activeWaves.isEmpty() || gridLayout.nodes.isEmpty() || waveSpeedPxPerMs <= 0f) {
            return@Canvas
        }

        val nowMs = animationTimeMs.toFloat()
        val nodeStates = ArrayList<NodeState>(gridLayout.nodes.size)

        for (node in gridLayout.nodes) {
            var displacement = Offset.Zero
            var dotAlpha = 0f
            var gridAlpha = 0f
            var growthPulse = 0f
            var jumpOffset = 0f

            for (wave in activeWaves) {
                val elapsed = nowMs - wave.startedAtMs
                if (elapsed < 0f || elapsed > WAVE_LIFETIME_MS) {
                    continue
                }

                val dx = node.x - wave.center.x
                val dy = node.y - wave.center.y
                val distance = sqrt(dx * dx + dy * dy)
                val arrivalTime = distance / waveSpeedPxPerMs
                val localTime = elapsed - arrivalTime

                val waveDotAlpha: Float
                val waveGridAlpha: Float
                val waveGrowthPulse: Float
                val waveJump: Float
                val waveWarp: Float

                if (localTime < 0f) {
                    val revealProgress = smoothStep((-localTime / REVEAL_LEAD_MS).coerceIn(0f, 1f))
                    waveDotAlpha = DOT_MAX_ALPHA * (1f - revealProgress)
                    waveGridAlpha = GRID_MAX_ALPHA * (1f - revealProgress)
                    waveGrowthPulse = 0f
                    waveJump = 0f
                    waveWarp = 0f
                } else if (localTime <= MOTION_DURATION_MS) {
                    val t = (localTime / MOTION_DURATION_MS).coerceIn(0f, 1f)
                    val jumpPulse = sin(t * PI).toFloat()
                    val motionPulse = jumpPulse.pow(2.4f)
                    waveDotAlpha = DOT_MAX_ALPHA
                    waveGridAlpha = GRID_MAX_ALPHA
                    waveGrowthPulse = motionPulse
                    waveJump = jumpPulse
                    waveWarp = motionPulse
                } else {
                    val fadeProgress = smoothStep(((localTime - MOTION_DURATION_MS) / FADE_DURATION_MS).coerceIn(0f, 1f))
                    val fadeScale = 1f - fadeProgress
                    waveDotAlpha = DOT_MAX_ALPHA * fadeScale
                    waveGridAlpha = GRID_MAX_ALPHA * fadeScale
                    waveGrowthPulse = 0f
                    waveJump = 0f
                    waveWarp = 0f
                }

                dotAlpha = max(dotAlpha, waveDotAlpha)
                gridAlpha = max(gridAlpha, waveGridAlpha)
                growthPulse = max(growthPulse, waveGrowthPulse)
                jumpOffset = max(jumpOffset, waveJump)

                if (waveWarp > 0f) {
                    val length = max(0.001f, distance)
                    val dirX = dx / length
                    val dirY = dy / length
                    displacement += Offset(
                        x = dirX * waveWarp * warpStrength,
                        y = dirY * waveWarp * warpStrength,
                    )
                }
            }

            val displaced = Offset(node.x, node.y) + displacement + Offset(0f, -jumpOffset * jumpHeight)
            val radius = baseDotRadius + node.radiusJitter + growthPulse * maxDotGrowth

            nodeStates += NodeState(
                displacedPosition = displaced,
                dotRadius = max(0.05f, radius),
                dotAlpha = dotAlpha.coerceIn(0f, 1f),
                gridAlpha = gridAlpha.coerceIn(0f, 1f),
            )
        }

        for (row in 0 until gridLayout.rows) {
            for (column in 0 until gridLayout.columns) {
                val index = row * gridLayout.columns + column
                val from = nodeStates[index]

                if (column < gridLayout.columns - 1) {
                    val right = nodeStates[index + 1]
                    val alpha = min(from.gridAlpha, right.gridAlpha)
                    if (alpha >= 0.002f) {
                        drawLine(
                            color = RIPPLE_COLOR.copy(alpha = alpha),
                            start = from.displacedPosition,
                            end = right.displacedPosition,
                            strokeWidth = gridLineWidth,
                        )
                    }
                }

                if (row < gridLayout.rows - 1) {
                    val down = nodeStates[index + gridLayout.columns]
                    val alpha = min(from.gridAlpha, down.gridAlpha)
                    if (alpha >= 0.002f) {
                        drawLine(
                            color = RIPPLE_COLOR.copy(alpha = alpha),
                            start = from.displacedPosition,
                            end = down.displacedPosition,
                            strokeWidth = gridLineWidth,
                        )
                    }
                }
            }
        }

        for (state in nodeStates) {
            if (state.dotAlpha < 0.01f) {
                continue
            }
            drawCircle(
                color = RIPPLE_COLOR.copy(alpha = state.dotAlpha),
                radius = state.dotRadius,
                center = state.displacedPosition,
            )
        }
    }
}

private fun buildGridLayout(
    canvasSize: IntSize,
    spacingPx: Float,
): GridLayout {
    if (canvasSize.width == 0 || canvasSize.height == 0 || spacingPx <= 0f) {
        return GridLayout(columns = 0, rows = 0, nodes = emptyList())
    }

    val canvasWidth = canvasSize.width.toFloat()
    val canvasHeight = canvasSize.height.toFloat()
    val columns = max(2, floor(canvasWidth / spacingPx).toInt() + 2)
    val rows = max(2, floor(canvasHeight / spacingPx).toInt() + 2)
    val startX = (canvasWidth - (columns - 1) * spacingPx) / 2f
    val startY = (canvasHeight - (rows - 1) * spacingPx) / 2f

    val nodes = ArrayList<GridNode>(columns * rows)
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            val seed = ((row + 1) * 73856093) xor ((column + 1) * 19349663)
            val jitter = (((seed and 1023) / 1023f) - 0.5f) * 0.5f
            nodes += GridNode(
                x = startX + column * spacingPx,
                y = startY + row * spacingPx,
                radiusJitter = jitter,
            )
        }
    }

    return GridLayout(
        columns = columns,
        rows = rows,
        nodes = nodes,
    )
}

private fun smoothStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
