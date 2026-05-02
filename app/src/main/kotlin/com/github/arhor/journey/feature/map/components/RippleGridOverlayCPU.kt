package com.github.arhor.journey.feature.map.components

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

val dotColor: Color = Color(0xFF82DCFF)
val backgroundColor: Color = Color.Transparent

@Composable
fun RippleGridOverlayCPU(
    waveOrigin: Offset,
    waveKeyNum: Number,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    val spacingPx = with(density) { 18.dp.toPx() }
    val baseRadiusPx = with(density) { 0.5.dp.toPx() }
    val maxGrowthPx = with(density) { 3.dp.toPx() }
    val jumpHeightPx = with(density) { 45.dp.toPx() }
    val jitterPx = with(density) { 0.12.dp.toPx() }

    val waveSpeed = 0.5f
    val waveLifeMs = 4_000L

    val preRevealDuration = 150f
    val motionDuration = 350f
    val postRevealDuration = 150f

    val waves = remember { mutableStateListOf<RippleWave>() }

    var frameTimeMs by remember {
        mutableLongStateOf(SystemClock.uptimeMillis())
    }

    var canvasSize by remember {
        mutableStateOf(IntSize.Zero)
    }

    val dots = remember(canvasSize, spacingPx, jitterPx) {
        createDotField(
            size = canvasSize,
            spacingPx = spacingPx,
            jitterPx = jitterPx,
        )
    }

    LaunchedEffect(waveKeyNum) {
        if (waveOrigin.isUsable()) {
            val now = SystemClock.uptimeMillis()
            frameTimeMs = now

            waves += RippleWave(
                origin = waveOrigin,
                startTimeMs = now,
            )
        }
    }

    LaunchedEffect(waves.size) {
        if (waves.isEmpty()) return@LaunchedEffect

        while (isActive && waves.isNotEmpty()) {
            val now = withFrameMillis { it }
            frameTimeMs = now

            waves.removeAll { wave ->
                now - wave.startTimeMs > waveLifeMs
            }
        }
    }

    Canvas(
        modifier = modifier.onSizeChanged { canvasSize = it }
    ) {
        if (backgroundColor.alpha > 0f) {
            drawRect(backgroundColor)
        }

        val now = frameTimeMs

        dots.forEach { dot ->
            var radius = baseRadiusPx + dot.radiusOffset
            var alpha = 0f
            var drawY = dot.position.y

            waves.forEach { wave ->
                val elapsed = (now - wave.startTimeMs).toFloat()
                if (elapsed < 0f) return@forEach

                val waveFade = 1f - elapsed / waveLifeMs.toFloat()
                if (waveFade <= 0f) return@forEach

                val dx = dot.position.x - wave.origin.x
                val dy = dot.position.y - wave.origin.y
                val distance = sqrt(dx * dx + dy * dy)

                val arrivalTime = distance / waveSpeed
                val localTime = elapsed - arrivalTime

                val phaseStart = -preRevealDuration
                val phaseEnd = motionDuration + postRevealDuration

                if (localTime !in phaseStart..phaseEnd) {
                    return@forEach
                }

                var localAlpha: Float
                var sizePulse = 0f
                var jumpPulse = 0f

                when {
                    localTime < 0f -> {
                        val t = (localTime + preRevealDuration) / preRevealDuration
                        localAlpha = smoothstep01(t)
                    }

                    localTime <= motionDuration -> {
                        val t = localTime / motionDuration
                        localAlpha = 1f
                        sizePulse = sizePulseShape(t)
                        jumpPulse = jumpPulseShape(t)
                    }

                    else -> {
                        val t = (localTime - motionDuration) / postRevealDuration
                        localAlpha = 1f - smoothstep01(t)
                    }
                }

                alpha += localAlpha * 0.95f * waveFade
                radius += sizePulse * maxGrowthPx * waveFade
                drawY -= jumpPulse * jumpHeightPx * waveFade
            }

            alpha = alpha.coerceIn(0f, 1f)

            if (alpha > 0.01f) {
                drawCircle(
                    color = dotColor.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(
                        x = dot.position.x,
                        y = drawY,
                    ),
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
internal fun RippleGridOverlayCPUPreview() {
    var waveOrigin by remember { mutableStateOf(Offset.Unspecified) }
    var waveKeyNum by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    waveOrigin = down.position
                    waveKeyNum++
                }
            }
    ) {
        RippleGridOverlayCPU(
            waveOrigin = waveOrigin,
            waveKeyNum = waveKeyNum,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/* ------------------------------------------ Internal implementation ------------------------------------------- */

private data class RippleDot(
    val position: Offset,
    val radiusOffset: Float,
)

internal data class RippleWave(
    val origin: Offset,
    val startTimeMs: Long,
)

private fun createDotField(
    size: IntSize,
    spacingPx: Float,
    jitterPx: Float,
): List<RippleDot> {
    if (size.width <= 0 || size.height <= 0 || spacingPx <= 0f) {
        return emptyList()
    }

    val dots = mutableListOf<RippleDot>()

    var y = spacingPx / 2f
    while (y < size.height) {
        var x = spacingPx / 2f

        while (x < size.width) {
            dots += RippleDot(
                position = Offset(x, y),
                radiusOffset = Random.nextFloat() * jitterPx,
            )

            x += spacingPx
        }

        y += spacingPx
    }

    return dots
}

private fun clamp01(value: Float): Float {
    return value.coerceIn(0f, 1f)
}

private fun smoothstep01(value: Float): Float {
    val t = clamp01(value)
    return t * t * (3f - 2f * t)
}

private fun sizePulseShape(t: Float): Float {
    return sin(t * PI).pow(2.4).toFloat()
}

private fun jumpPulseShape(t: Float): Float {
    return sin(t * PI).toFloat()
}

private fun Offset.isUsable(): Boolean {
    return !x.isNaN() &&
        !y.isNaN() &&
        !x.isInfinite() &&
        !y.isInfinite()
}
