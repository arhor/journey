package com.github.arhor.journey.feature.map.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Brush.Companion.verticalGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

val LineColor = Color(0xFF8C855B)

@Composable
fun ResetCameraButton(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .drawWithCache {
                val strokeWidth = 3.dp.toPx()
                val halfStrokeW = strokeWidth / 2f
                val inset = 12.dp.toPx() + halfStrokeW

                val (w, h) = size
                val center = size.center

                val outerCornerCut = 0.06f
                val innerCornerCut = 0.05f

                val innerW = w - inset * 2f
                val innerH = h - inset * 2f

                val outerCenterX = w / 2f
                val outerCenterY = h / 2f

                val innerCenterX = innerW / 2f
                val innerCenterY = innerH / 2f

                val innerCut = minOf(innerW, innerH) * innerCornerCut
                val outerCut = minOf(w, h) * outerCornerCut

                val innerMaxX = inset + innerW
                val innerMaxY = inset + innerH

                val frame1Path = Path().apply {
                    // Bottom Center
                    moveTo(innerCenterX * 0.9f, innerMaxY)

                    // Bottom inner gap 1
                    lineTo(outerCenterX * 1.40f, innerMaxY)
                    moveTo(outerCenterX * 1.45f, innerMaxY)

                    // Bottom-right Corner
                    lineTo(innerMaxX - innerCut, innerMaxY)
                    lineTo(innerMaxX, innerMaxY - innerCut)

                    lineTo(innerMaxX, innerCenterY * 0.8f)
                    moveTo(innerMaxX, innerCenterY * 0.7f)

                    // Top-right Corner
                    lineTo(innerMaxX, inset + innerCut)
                    lineTo(innerMaxX - innerCut, inset)

                    // Top-center
                    lineTo(innerCenterX * 1.1f, inset)
                    lineTo(innerCenterX * 1.0f, 0f)

                    // Top-left Corner
                    lineTo(outerCut, 0f)
                    lineTo(0f, outerCut)

                    // Left outer gap 1
                    lineTo(0f, outerCenterY * 0.50f)
                    moveTo(0f, outerCenterY * 0.55f)

                    // Left outer gap 2
                    lineTo(0f, outerCenterY * 0.60f)
                    moveTo(0f, outerCenterY * 0.65f)

                    // Left outer gap 3
                    lineTo(0f, outerCenterY * 0.75f)
                    moveTo(0f, outerCenterY * 0.85f)

                    // Bottom-left Corner
                    lineTo(0f, h - outerCut)
                    lineTo(outerCut, h)

                    // Bottom-center
                    lineTo(outerCenterX * 0.4f, h)
                }
                val frame2Path = Path().apply {
                    // Top Center
                    moveTo(innerCenterX * 0.975f, inset)

                    // Top-Left Corner
                    lineTo(inset + innerCut, inset)
                    lineTo(inset, inset + innerCut)

                    // Bottom-Left Corner
                    lineTo(inset, innerMaxY - innerCut)
                    lineTo(inset + innerCut, innerMaxY)

                    // Bottom inner-to-outer diagonal line
                    lineTo(innerCenterX * 0.7f, innerMaxY)
                    lineTo(innerCenterX * 0.8f, h)

                    // Bottom outer gap 1
                    lineTo(outerCenterX * 1.15f, h)
                    moveTo(outerCenterX * 1.20f, h)

                    // Bottom outer gap 2
                    lineTo(outerCenterX * 1.30f, h)
                    moveTo(outerCenterX * 1.35f, h)

                    // Bottom-right Corner
                    lineTo(w - outerCut, h)
                    lineTo(w, h - outerCut)

                    // Right outer gap 1
                    lineTo(w, outerCenterY * 1.55f)
                    moveTo(w, outerCenterY * 1.50f)

                    // Right outer gap 2
                    lineTo(w, outerCenterY * 1.40f)
                    moveTo(w, outerCenterY * 1.35f)

                    // Top-right Corner
                    lineTo(w, outerCut)
                    lineTo(w - outerCut, 0f)

                    // Top Center
                    lineTo(outerCenterX * 1.65f, 0f)
                    moveTo(outerCenterX * 1.10f, 0f)

                    lineTo(outerCenterX * 1.025f, 0f)
                }
                val topLeftAccent = Path().apply {
                    val longSegmentWidth = w * 0.1f
                    val slashWidth = w * 0.03f
                    val gap = w * 0.02f

                    // long horizontal segment
                    moveTo(outerCut, -inset - halfStrokeW)
                    lineTo(outerCut + longSegmentWidth, -inset - halfStrokeW)
                    lineTo(outerCut + longSegmentWidth + strokeWidth, -inset + halfStrokeW)
                    lineTo(outerCut + strokeWidth, -inset + halfStrokeW)
                    close()

                    var x = outerCut + longSegmentWidth + gap

                    repeat(3) {
                        moveTo(x, -inset - halfStrokeW)
                        lineTo(x + slashWidth, -inset - halfStrokeW)
                        lineTo(x + slashWidth + strokeWidth, -inset + halfStrokeW)
                        lineTo(x + strokeWidth, -inset + halfStrokeW)
                        close()

                        x += slashWidth + gap
                    }
                }
                val bottomRightAccent = Path().apply {
                    val gap = w * 0.02f
                    val smallWidth = w * 0.025f

                    // ---- single slash near the corner ----
                    moveTo(w + strokeWidth, h * 0.97f)
                    lineTo(w + strokeWidth, (h * 0.97f) + strokeWidth)
                    lineTo((w * 0.97f) + strokeWidth, h + strokeWidth)
                    lineTo(w * 0.97f, h + strokeWidth)
                    close()

                    // ---- 3 small slashes below ----
                    val rowY = h + inset
                    var xRight = w * 0.9f

                    repeat(3) {
                        moveTo(xRight, rowY)
                        lineTo(xRight - smallWidth, rowY)
                        lineTo(xRight - smallWidth - strokeWidth, rowY + strokeWidth)
                        lineTo(xRight - strokeWidth, rowY + strokeWidth)
                        close()

                        xRight -= smallWidth + gap
                    }
                }

                val colors = listOf(LineColor, Color.Transparent)
                val frameStyle = Stroke(width = strokeWidth)
                val frame1Brush = sweepGradient(
                    colors = colors,
                    center = center,
                    rotateDegrees = 120.0f,
                )
                val frame2Brush = sweepGradient(
                    colors = colors,
                    center = center,
                    rotateDegrees = -90.0f,
                )

                onDrawBehind {
                    drawPath(
                        path = frame1Path,
                        brush = frame1Brush,
                        style = frameStyle
                    )
                    drawPath(
                        path = frame2Path,
                        brush = frame2Brush,
                        style = frameStyle
                    )
                    drawPath(
                        path = topLeftAccent,
                        color = LineColor,
                    )
                    drawPath(
                        path = bottomRightAccent,
                        color = LineColor,
                    )
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF1E1E1D)
internal fun ResetCameraButtonPreview() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ResetCameraButton(
            modifier = Modifier.size(320.dp),
        ) {
            CompassRoseIcon(
                modifier = Modifier.fillMaxSize(0.80f),
                color = LineColor,
                cutoutColor = Color.Transparent,
            )
        }
    }
}

@Stable
private fun sweepGradient(
    colors: List<Color>,
    center: Offset,
    rotateDegrees: Float = 0f
): Brush = Brush
    .sweepGradient(colors = colors, center = center)
    .toShaderBrush()
    .apply {
        transform = Matrix().apply {
            resetToPivotedTransform(
                pivotX = center.x,
                pivotY = center.y,
                rotationZ = rotateDegrees
            )
        }
    }

@Stable
private fun sweepGradient(
    vararg colorStops: Pair<Float, Color>,
    center: Offset,
    rotateDegrees: Float = 0f
): Brush = Brush
    .sweepGradient(*colorStops, center = center)
    .toShaderBrush()
    .apply {
        transform = Matrix().apply {
            resetToPivotedTransform(
                pivotX = center.x,
                pivotY = center.y,
                rotationZ = rotateDegrees
            )
        }
    }

@Stable
private fun Brush.toShaderBrush(): ShaderBrush =
    when (this) {
        is ShaderBrush -> this
        is SolidColor -> verticalGradient(listOf(value, value)) as ShaderBrush
    }
