package com.github.arhor.journey.feature.hero.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.core.ui.components.ResourceTypeIcon
import com.github.arhor.journey.core.ui.components.resourceTypeLabel

@Composable
internal fun ResourceRow(
    resourceType: ResourceType,
    amount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ResourceTypeIcon(
                resourceType = resourceType,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = resourceTypeLabel(resourceType),
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
