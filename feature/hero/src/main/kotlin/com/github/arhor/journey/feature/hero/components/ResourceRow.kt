package com.github.arhor.journey.feature.hero.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.feature.hero.R

@Composable
internal fun ResourceRow(
    resourceType: ResourceType,
    amount: Int,
) {
    val painter = rememberResourceTypePainter(resourceType)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = Color.Unspecified,
            )
            Text(
                text = stringResource(resourceLabelRes(resourceType)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = amount.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun  rememberResourceTypePainter(resourceType: ResourceType): Painter {
    val context = LocalContext.current
    val drawableId = remember(context.packageName, resourceType) {
        context.resolveDrawableId(resourceType.drawableName)
    }

    require(drawableId != 0) {
        "Missing drawable resource for ${resourceType.typeId}."
    }

    return painterResource(id = drawableId)
}

private fun resourceLabelRes(resourceType: ResourceType): Int =
    when (resourceType) {
        ResourceType.WOOD -> R.string.hero_resource_wood
        ResourceType.COAL -> R.string.hero_resource_coal
        ResourceType.STONE -> R.string.hero_resource_stone
    }

private fun Context.resolveDrawableId(drawableName: String): Int =
    resources.getIdentifier(drawableName, "drawable", packageName)
