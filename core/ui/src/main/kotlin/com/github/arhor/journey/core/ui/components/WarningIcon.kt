package com.github.arhor.journey.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.DefaultFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Custom icons for the Journey application.
 */
object JourneyIcons {
    private var _warningNew: ImageVector? = null
    private var _warningOld: ImageVector? = null

    val WarningNew: ImageVector
        get() {
            if (_warningNew != null) {
                return _warningNew!!
            }
            _warningNew = ImageVector.Builder(
                name = "WarningNew",
                defaultWidth = 24f.dp,
                defaultHeight = 24f.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                customPath {
                    moveTo(12.0f, 5.99f)
                    lineTo(19.53f, 19.0f)
                    lineTo(4.47f, 19.0f)
                    lineTo(12.0f, 5.99f)
                    moveTo(2.74f, 18.0f)
                    curveToRelative(-0.77f, 1.33f, 0.19f, 3.0f, 1.73f, 3.0f)
                    horizontalLineToRelative(15.06f)
                    curveToRelative(1.54f, 0.0f, 2.5f, -1.67f, 1.73f, -3.0f)
                    lineTo(13.73f, 4.99f)
                    curveToRelative(-0.77f, -1.33f, -2.69f, -1.33f, -3.46f, 0.0f)
                    lineTo(2.74f, 18.0f)
                    close()
                    moveTo(11.0f, 11.0f)
                    verticalLineToRelative(2.0f)
                    curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                    reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                    verticalLineToRelative(-2.0f)
                    curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                    reflectiveCurveToRelative(-1.0f, 0.45f, -1.0f, 1.0f)
                    close()
                    moveTo(11.0f, 16.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(-2.0f)
                    close()
                }
            }.build()
            return _warningNew!!
        }

    val WarningOld: ImageVector
        get() {
            if (_warningOld != null) return _warningOld!!
            _warningOld = ImageVector.Builder(
                name = "WarningOld",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                // Triangle Outline with thick stroke and rounded joins/caps
                path(
                    stroke = SolidColor(Accent),
                    strokeLineWidth = 3.2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round
                ) {
                    moveTo(12f, 4f)
                    lineTo(21f, 19.5f)
                    lineTo(3f, 19.5f)
                    close()
                }

                // Vertical bar of exclamation mark (thick and slightly tapered)
                path(fill = SolidColor(Accent)) {
                    moveTo(10.8f, 8.5f)
                    lineTo(13.2f, 8.5f)
                    lineTo(12.8f, 14.5f)
                    lineTo(11.2f, 14.5f)
                    close()
                }

                // Dot of exclamation mark (larger circle)
                path(fill = SolidColor(Accent)) {
                    val centerY = 17.8f
                    val radius = 1.4f
                    moveTo(12f, centerY - radius)
                    curveTo(12f + radius, centerY - radius, 12f + radius, centerY + radius, 12f, centerY + radius)
                    curveTo(12f - radius, centerY + radius, 12f - radius, centerY - radius, 12f, centerY - radius)
                    close()
                }
            }.build()
            return _warningOld!!
        }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A)
private fun WarningIconPreview() {
    Column {
        Icon(
            imageVector = JourneyIcons.WarningNew,
            contentDescription = "Warning",
            modifier = Modifier.size(68.dp),
            tint = Accent
        )
    }
}


inline fun ImageVector.Builder.customPath(
    fillAlpha: Float = 1f,
    strokeAlpha: Float = 1f,
    pathFillType: PathFillType = DefaultFillType,
    pathBuilder: PathBuilder.() -> Unit
) = path(
    fill = SolidColor(Accent),
    fillAlpha = fillAlpha,
    stroke = null,
    strokeAlpha = strokeAlpha,
    strokeLineWidth = 1f,
    strokeLineCap = StrokeCap.Butt,
    strokeLineJoin = StrokeJoin.Bevel,
    strokeLineMiter = 1f,
    pathFillType = pathFillType,
    pathBuilder = pathBuilder
)
