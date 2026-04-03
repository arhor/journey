package com.github.arhor.journey.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun WarningIcon(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = 4.dp.toPx()
        val margin = 8.dp.toPx()

        val triangle = Path().apply {
            moveTo(size.width / 2f, margin)
            lineTo(size.width - margin, size.height - margin)
            lineTo(margin, size.height - margin)
            close()
        }

        drawPath(
            path = triangle,
            color = Accent,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        drawRoundRect(
            color = Accent,
            topLeft = Offset(
                x = size.width / 2f - stroke / 2f,
                y = size.height * 0.30f,
            ),
            size = Size(
                width = stroke,
                height = size.height * 0.28f,
            ),
            cornerRadius = CornerRadius(stroke / 2f),
        )

        drawCircle(
            color = Accent,
            radius = stroke * 0.75f,
            center = Offset(
                x = size.width / 2f,
                y = size.height * 0.72f,
            ),
        )
    }
}

@Composable
@Preview(showBackground = true)
private fun WarningIconPreview() {
    Box(
        modifier = Modifier
            .size(78.dp),
        contentAlignment = Alignment.Center,
    ) {
        WarningIcon(
            modifier = Modifier
                .size(68.dp)
                .align(Alignment.Center)
        )
    }
}
