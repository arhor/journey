package com.github.arhor.journey.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.core.ui.components.ResourceTypeIcon
import com.github.arhor.journey.core.ui.components.resourceTypeLabel

internal const val MAP_HUD_TEST_TAG = "mapHud"
internal const val MAP_HUD_HERO_BUTTON_TEST_TAG = "mapHud:hero"
internal const val MAP_HUD_SETTINGS_BUTTON_TEST_TAG = "mapHud:settings"

@Composable
internal fun MapPlayerHud(
    state: MapHudUiState,
    onHeroClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayState = state.toDisplayState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MAP_HUD_TEST_TAG),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroHudButton(
                heroInitial = displayState.heroInitial,
                levelLabel = displayState.levelLabel,
                onClick = onHeroClick,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                displayState.resources.forEach {
                    ResourceAmountChip(state = it)
                }

                FilledTonalIconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(MAP_HUD_SETTINGS_BUTTON_TEST_TAG),
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.map_hud_settings_content_description),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroHudButton(
    heroInitial: String,
    levelLabel: String,
    onClick: () -> Unit,
) {
    val contentDescriptionText = stringResource(R.string.map_hud_hero_content_description, levelLabel)

    TextButton(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = contentDescriptionText }
            .testTag(MAP_HUD_HERO_BUTTON_TEST_TAG),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = heroInitial,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 8.dp, y = 8.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = levelLabel,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ResourceAmountChip(
    state: MapHudResourceUiModel,
) {
    val resourceLabel = resourceTypeLabel(state.resourceType)
    val contentDescriptionText = stringResource(
        R.string.map_hud_resource_content_description,
        resourceLabel,
        state.amount,
    )

    Row(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .semantics { contentDescription = contentDescriptionText },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ResourceTypeIcon(
            resourceType = state.resourceType,
            modifier = Modifier.size(30.dp),
            contentDescription = null,
        )
        Text(
            text = state.amountLabel,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun MapHudUiState.toDisplayState(): MapHudDisplayState =
    when (this) {
        is MapHudUiState.Content -> {
            MapHudDisplayState(
                heroInitial = heroInitial,
                levelLabel = levelLabel,
                resources = resources,
            )
        }

        MapHudUiState.Loading,
        MapHudUiState.Unavailable -> {
            MapHudDisplayState(
                heroInitial = "?",
                levelLabel = "Lv --",
                resources = placeholderResources(),
            )
        }
    }

private fun placeholderResources(): List<MapHudResourceUiModel> =
    ResourceType.entries.map { resourceType ->
        MapHudResourceUiModel(
            resourceType = resourceType,
            amount = 0,
            amountLabel = "0",
        )
    }

@Immutable
private data class MapHudDisplayState(
    val heroInitial: String,
    val levelLabel: String,
    val resources: List<MapHudResourceUiModel>,
)

@Composable
@PreviewLightDark
private fun MapPlayerHudDefaultPreview() {
    MaterialTheme {
        MapPlayerHud(
            state = MapHudUiState.Content(
                heroInitial = "A",
                levelLabel = "Lv 7",
                resources = listOf(
                    MapHudResourceUiModel(ResourceType.SCRAP, amount = 18, amountLabel = "18"),
                    MapHudResourceUiModel(ResourceType.COMPONENTS, amount = 7, amountLabel = "7"),
                    MapHudResourceUiModel(ResourceType.FUEL, amount = 0, amountLabel = "0"),
                ),
            ),
            onHeroClick = {},
            onSettingsClick = {},
        )
    }
}

@Composable
@PreviewLightDark
private fun MapPlayerHudLargeNumbersPreview() {
    MaterialTheme {
        MapPlayerHud(
            state = MapHudUiState.Content(
                heroInitial = "A",
                levelLabel = "Lv 12",
                resources = listOf(
                    MapHudResourceUiModel(ResourceType.SCRAP, amount = 1_250, amountLabel = "1.2K"),
                    MapHudResourceUiModel(ResourceType.COMPONENTS, amount = 12_300, amountLabel = "12K"),
                    MapHudResourceUiModel(ResourceType.FUEL, amount = 1_300_000, amountLabel = "1.3M"),
                ),
            ),
            onHeroClick = {},
            onSettingsClick = {},
        )
    }
}
