package com.github.arhor.journey.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Custom icons for the Journey application.
 */
object JourneyIcons {
    private var _warningNew: ImageVector? = null

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
                // Triangle
                path(
                    stroke = SolidColor(Accent),
                    strokeLineWidth = 1.5f,
                    strokeLineJoin = StrokeJoin.Round,
                    strokeLineCap = StrokeCap.Round,
                ) {
                    moveTo(12.0f, 4.5f)
                    lineTo(21.0f, 19.5f)
                    lineTo(3.0f, 19.5f)
                    close()
                }

                // Vertical line
                path(fill = SolidColor(Accent)) {
                    moveTo(10.8f, 9.5f)
                    lineTo(13.2f, 9.5f)
                    lineTo(12.8f, 15.0f)
                    lineTo(11.2f, 15.0f)
                    close()
                }

                // Dot
                path(fill = SolidColor(Accent)) {
                    moveTo(11.2f, 16.3f)
                    horizontalLineToRelative(1.7f)
                    verticalLineToRelative(1.7f)
                    horizontalLineToRelative(-1.7f)
                    close()
                }
            }.build()
            return _warningNew!!
        }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A)
private fun WarningIconPreview() {
    Column {
        Icon(
            imageVector = JourneyIcons.WarningNew,
            contentDescription = "Warning",
            modifier = Modifier.size(100.dp),
            tint = Accent
        )
    }
}
