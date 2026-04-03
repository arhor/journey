package com.github.arhor.journey.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
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
            _warningNew = ImageVector
                .Builder(
                    name = "WarningNew",
                    defaultWidth = 24f.dp,
                    defaultHeight = 24f.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                )
                .apply {
                    path(fill = SolidColor(Accent)) {
                        // Triangle
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

                        // Vertical line
                        moveTo(10.8f, 9.5f)
                        lineTo(13.2f, 9.5f)
                        lineTo(12.8f, 15.0f)
                        lineTo(11.2f, 15.0f)
                        close()

                        // Dot
                        moveTo(11.2f, 16.3f)
                        horizontalLineToRelative(1.7f)
                        verticalLineToRelative(1.7f)
                        horizontalLineToRelative(-1.7f)
                        close()
                    }
                }
                .build()
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
