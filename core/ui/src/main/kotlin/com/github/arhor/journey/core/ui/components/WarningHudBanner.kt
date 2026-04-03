package com.github.arhor.journey.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val PanelBackgroundTop = Color(0xFF161411)
internal val PanelBackgroundBottom = Color(0xFF0C0B0A)
internal val OuterBorder = Color(0xFF6E5D44)
internal val InnerBorder = Color(0xFFA8895B)
val Accent = Color(0xFFD2A15E)
internal val MainText = Color(0xFFE7E1D8)
internal val SecondaryText = Color(0xFF8E877E)

@Composable
fun WarningHudBanner(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    headline: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
    icon: @Composable BoxScope.() -> Unit = {
        Icon(
            imageVector = JourneyIcons.WarningNew,
            contentDescription = "Warning",
            modifier = Modifier
                .size(68.dp)
                .align(Alignment.Center),
            tint = Accent
        )
    },
    trailingDecoration: @Composable BoxScope.() -> Unit = {
        DefaultHazardStripes(
            modifier = Modifier
                .width(120.dp)
                .height(28.dp)
                .align(Alignment.CenterEnd),
        )
    },
) {
    val panelShape = rememberSciFiPanelShape()

    Box(
        modifier = modifier
            .heightIn(min = 112.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(panelShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            PanelBackgroundTop,
                            PanelBackgroundBottom,
                        ),
                    ),
                )
                .panelTexture()
                .border(width = 1.dp, color = OuterBorder, shape = panelShape)
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(4.dp)
                .clip(panelShape)
                .border(
                    width = 1.dp,
                    color = InnerBorder.copy(alpha = 0.35f),
                    shape = panelShape,
                )
        )

        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(78.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                title()
                Spacer(modifier = Modifier.height(4.dp))
                headline()
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    subtitle()
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(32.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                trailingDecoration()
            }
        }
    }
}

@Composable
internal fun DefaultHazardStripes(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stripes = 5
        val stripeWidth = size.width / 7.5f
        val gap = stripeWidth * 0.22f

        repeat(stripes) { index ->
            val left = index * (stripeWidth + gap)

            val stripe = Path().apply {
                moveTo(left, size.height)
                lineTo(left + stripeWidth * 0.32f, size.height)
                lineTo(left + stripeWidth, 0f)
                lineTo(left + stripeWidth * 0.68f, 0f)
                close()
            }

            drawPath(
                path = stripe,
                color = Accent.copy(alpha = 0.18f),
            )
        }
    }
}

private fun Modifier.panelTexture(): Modifier = drawWithCache {
    val scanlineColor = Color.White.copy(alpha = 0.02f)
    val topGlow = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.06f),
            Color.Transparent,
            Color.Black.copy(alpha = 0.12f),
        ),
    )

    onDrawWithContent {
        drawContent()

        var y = 0f
        val step = 6.dp.toPx()
        while (y < size.height) {
            drawLine(
                color = scanlineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += step
        }

        drawRect(brush = topGlow)
    }
}

@Composable
fun rememberSciFiPanelShape(
    cornerCut: Dp = 9.dp,
    leftInset1: Dp = 12.dp,
    leftInset2: Dp = 9.dp,
    notchDepth: Dp = 9.dp,
    notchTopFraction: Float = 0.65f,
    notchHeightFraction: Float = 0.20f,
    notchSlant: Dp = 9.dp,
): Shape {
    val density = LocalDensity.current

    return remember(density, cornerCut, leftInset1, leftInset2, notchDepth, notchTopFraction, notchHeightFraction, notchSlant) {
        val cutPx = with(density) { cornerCut.toPx() }
        val inset1Px = with(density) { leftInset1.toPx() }
        val inset2Px = with(density) { leftInset2.toPx() }
        val stepPx = with(density) { notchDepth.toPx() }
        val slantPx = with(density) { notchSlant.toPx() }

        GenericShape { size, _ ->
            val w = size.width
            val h = size.height

            val nTop = h * notchTopFraction

            // Drawing Clockwise
            // Top edge
            moveTo(inset1Px, 0f)
            lineTo(w - cutPx, 0f)
            // Top-right chamfer
            lineTo(w, cutPx)
            // Right edge
            lineTo(w, h - cutPx)
            // Bottom-right chamfer
            lineTo(w - cutPx, h)
            // Bottom edge (ends at indented x)
            lineTo(inset2Px + stepPx, h)
            // Bottom-left corner (viewed top-down: \) starts from inner vertical
            lineTo(stepPx, h - cutPx)
            // Notch vertical inner (all the way to top slant)
            lineTo(stepPx, nTop + slantPx)
            // Notch top slant (outward and up: \)
            lineTo(10f, nTop)
            // Left vertical top
            lineTo(10f, cutPx)
            // Close connects back to (insetPx, 0f) with top-left chamfer (UP and RIGHT: /)
            close()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WarningHudBannerPreview() {
    Box(
        modifier = Modifier
            .background(Color(0xFF050505))
            .padding(16.dp)
    ) {
        WarningHudBanner(
            modifier = Modifier.fillMaxWidth(),
            title = {
                Text(
                    text = "WARNING",
                    color = Accent,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                    ),
                )
            },
            headline = {
                Text(
                    text = "HOSTILE ACTIVITY DETECTED",
                    color = MainText,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    ),
                )
            },
            subtitle = {
                Text(
                    text = "Stay alert.",
                    color = SecondaryText,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
        )
    }
}
