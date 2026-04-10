package com.github.arhor.journey.feature.map.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CompassRoseIcon(
    modifier: Modifier = Modifier,
    color: Color = LineColor,
    cutoutColor: Color = Color.Transparent,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    Spacer(
        modifier = modifier
            .aspectRatio(1f)
            .drawWithCache {
                val s = size.minDimension
                val c = size.center

                val outerRadius = s * 0.39f
                val innerRadius = s * 0.26f
                val strokeWidth = s * 0.015f

                val outerArcSize = Size(outerRadius * 2f, outerRadius * 2f)
                val innerArcSize = Size(innerRadius * 2f, innerRadius * 2f)

                val outerArcTopLeft = Offset(c.x - outerRadius, c.y - outerRadius)
                val innerArcTopLeft = Offset(c.x - innerRadius, c.y - innerRadius)

                fun polar(radius: Float, angleDeg: Float): Offset {
                    val radians = Math.toRadians(angleDeg.toDouble())
                    return Offset(
                        x = c.x + cos(radians).toFloat() * radius,
                        y = c.y + sin(radians).toFloat() * radius,
                    )
                }

                val outerStroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                val innerStroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)

                // Pointer shape pointing up; reused with rotation
                val coreFillRadius = s * 0.05f

                val pointerTipY = c.y - s * 0.32f
                val pointerShoulderY = c.y - s * 0.07f
                val pointerBaseY = c.y + s * -0.080f

                val splitGap = s * 0.0025f
                val shoulderHalfWidth = s * 0.04f

                val pointerPath = Path().apply {
                    // left half
                    moveTo(c.x - splitGap, pointerBaseY)
                    lineTo(c.x - shoulderHalfWidth, pointerShoulderY)
                    lineTo(c.x, pointerTipY)
                    close()

                    // right half
                    moveTo(c.x + splitGap, pointerBaseY)
                    lineTo(c.x + shoulderHalfWidth, pointerShoulderY)
                    lineTo(c.x, pointerTipY)
                    close()
                }

                val seamStart = Offset(c.x, pointerBaseY + s * 0.006f)
                val seamEnd = Offset(c.x, pointerTipY + s * 0.030f)

                val fontSize = with(density) { (s * 0.092f).toSp() }

                onDrawBehind {
                    // Outer ring
                    listOf(191f, 281.0f, 11.0f, 100.0f).forEach { startAngle ->
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = 68f,
                            useCenter = false,
                            topLeft = outerArcTopLeft,
                            size = outerArcSize,
                            style = outerStroke,
                        )
                    }

                    // Small ticks on outer ring
                    listOf(225f, 315f, 45f, 135f).forEach { angle ->
                        drawLine(
                            color = color,
                            start = polar(outerRadius - strokeWidth * 2.0f, angle),
                            end = polar(outerRadius, angle),
                            strokeWidth = strokeWidth * 0.9f,
                            cap = StrokeCap.Square,
                        )
                    }

                    // Inner ring
                    val totalSweep = 60f
                    val centerGap = 8f
                    val segmentSweep = (totalSweep - centerGap) / 2f

                    listOf(195f, 286f, 14f, 106f).forEach { startAngle ->
                        // left half
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = segmentSweep,
                            useCenter = false,
                            topLeft = innerArcTopLeft,
                            size = innerArcSize,
                            style = innerStroke,
                        )

                        // right half
                        drawArc(
                            color = color,
                            startAngle = startAngle + segmentSweep + centerGap,
                            sweepAngle = segmentSweep,
                            useCenter = false,
                            topLeft = innerArcTopLeft,
                            size = innerArcSize,
                            style = innerStroke,
                        )
                    }

                    // N, E, S, W pointers
                    listOf(0f, 90f, 180f, 270f).forEach { rotation ->
                        rotate(
                            degrees = rotation,
                            pivot = c,
                        ) {
                            drawPath(
                                path = pointerPath,
                                color = color,
                            )
                            drawLine(
                                color = cutoutColor,
                                start = seamStart,
                                end = seamEnd,
                                strokeWidth = s * 0.010f,
                                cap = StrokeCap.Round,
                            )
                        }
                    }

                    // Center dot
                    drawCircle(
                        color = color,
                        radius = coreFillRadius,
                        center = c,
                    )

                    fun drawCenteredLabel(
                        text: String,
                        anchor: Offset,
                    ) {
                        val layout = textMeasurer.measure(
                            text = text,
                            style = TextStyle(
                                color = color,
                                fontSize = fontSize,
                                fontWeight = FontWeight.Medium,
                            ),
                        )

                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(
                                x = anchor.x - layout.size.width / 2f,
                                y = anchor.y - layout.size.height / 2f,
                            ),
                        )
                    }

                    drawCenteredLabel(
                        text = "N",
                        anchor = Offset(c.x, c.y - s * 0.39f),
                    )
                    drawCenteredLabel(
                        text = "E",
                        anchor = Offset(c.x + s * 0.39f, c.y),
                    )
                    drawCenteredLabel(
                        text = "S",
                        anchor = Offset(c.x, c.y + s * 0.39f),
                    )
                    drawCenteredLabel(
                        text = "W",
                        anchor = Offset(c.x - s * 0.39f, c.y),
                    )
                }
            }
    )
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF1E1E1D)
internal fun CompassRoseIconPreview() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CompassRoseIcon(
            modifier = Modifier.size(320.dp),
        )
    }
}
