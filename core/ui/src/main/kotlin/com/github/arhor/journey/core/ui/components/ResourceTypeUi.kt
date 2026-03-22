package com.github.arhor.journey.core.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.core.ui.R

@Composable
fun ResourceTypeIcon(
    resourceType: ResourceType,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Icon(
        painter = rememberResourceTypePainter(resourceType),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = Color.Unspecified,
    )
}

@Composable
fun resourceTypeLabel(resourceType: ResourceType): String =
    stringResource(resourceTypeLabelRes(resourceType))

@StringRes
fun resourceTypeLabelRes(resourceType: ResourceType): Int =
    when (resourceType) {
        ResourceType.WOOD -> R.string.core_ui_resource_wood
        ResourceType.COAL -> R.string.core_ui_resource_coal
        ResourceType.STONE -> R.string.core_ui_resource_stone
    }

@Composable
private fun rememberResourceTypePainter(resourceType: ResourceType): Painter {
    val context = LocalContext.current
    val drawableId = remember(context.packageName, resourceType) {
        context.resolveDrawableId(resourceType.drawableName)
    }

    require(drawableId != 0) {
        "Missing drawable resource for ${resourceType.typeId}."
    }

    return painterResource(id = drawableId)
}

private fun Context.resolveDrawableId(drawableName: String): Int =
    resources.getIdentifier(drawableName, "drawable", packageName)
