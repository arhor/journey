package com.github.arhor.journey.feature.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.core.ui.components.Accent
import com.github.arhor.journey.core.ui.components.ResourceTypeIcon
import com.github.arhor.journey.core.ui.components.rememberSciFiPanelShape
import com.github.arhor.journey.core.ui.components.resourceTypeLabel

internal const val MAP_HUD_TEST_TAG = "mapHud"
internal const val MAP_HUD_HERO_BUTTON_TEST_TAG = "mapHud:hero"
internal const val MAP_HUD_SETTINGS_BUTTON_TEST_TAG = "mapHud:settings"

private val HudPanelTop = Color(0xF41D252E)
private val HudPanelBottom = Color(0xE70B1015)
private val HudStroke = Color(0xFF4E5D68)
private val HudInnerStroke = Color(0xFF8AA0AC)
private val HudText = Color(0xFFF3F6F7)
private val HudMutedText = Color(0xFFAAB5BA)
private val HudBadge = Color(0xFF0F1720)
private val HudGlow = Color(0xFF59A4D7)
private val HudWarning = Color(0xFFE0A66A)

@Composable
internal fun MapPlayerHud(
    state: MapHudUiState,
    onHeroClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayState = state.toDisplayState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MAP_HUD_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HudPanel(shape = rememberSciFiPanelShape()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroHudButton(
                    heroInitial = displayState.heroInitial,
                    levelLabel = displayState.levelLabel,
                    onClick = onHeroClick,
                )
                HeroXpBlock(
                    xpInLevel = displayState.xpInLevel,
                    xpToNextLevel = displayState.xpToNextLevel,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    displayState.resources.forEach { resource ->
                        ResourceAmountChip(state = resource)
                    }
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag(MAP_HUD_SETTINGS_BUTTON_TEST_TAG),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.map_hud_settings_content_description),
                            tint = HudText,
                        )
                    }
                }
            }
        }

        HudInfoCard(
            title = stringResource(R.string.map_hud_weather_title),
            headline = stringResource(R.string.map_hud_weather_condition),
            subtitle = stringResource(R.string.map_hud_weather_details),
            contentDescription = stringResource(R.string.map_weather_content_description),
            icon = {
                Icon(
                    imageVector = Icons.Outlined.CloudQueue,
                    contentDescription = null,
                    tint = HudGlow,
                )
            },
            modifier = Modifier.widthIn(min = 188.dp, max = 190.dp),
        )
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
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = HudBadge,
                border = BorderStroke(1.dp, HudStroke),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = heroInitial,
                        color = HudText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = levelLabel.uppercase(),
                color = HudMutedText,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp,
            )
        }
    }
}

@Composable
private fun HeroXpBlock(
    xpInLevel: Long,
    xpToNextLevel: Long,
    modifier: Modifier = Modifier,
) {
    val safeTotal = xpToNextLevel.coerceAtLeast(1L)
    val progress = (xpInLevel.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(
                R.string.map_hud_xp_label,
                xpInLevel.toInt(),
                xpToNextLevel.toInt(),
            ),
            color = HudMutedText,
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.1.sp,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFF0B1218))
                .border(1.dp, HudStroke.copy(alpha = 0.75f), RoundedCornerShape(999.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF7FD7FF), Color(0xFF3A83C0)),
                        ),
                    ),
            )
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

    Column(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .semantics { contentDescription = contentDescriptionText },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ResourceTypeIcon(
            resourceType = state.resourceType,
            modifier = Modifier.size(22.dp),
            contentDescription = null,
        )
        Text(
            text = state.amountLabel,
            color = HudText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HudInfoCard(
    title: String,
    headline: String,
    subtitle: String,
    contentDescription: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    HudPanel(
        modifier = modifier
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                icon()
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title.uppercase(),
                    color = HudText.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.4.sp,
                )
                Text(
                    text = headline,
                    color = HudText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = Color(0xFFE5EEF3),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HudPanel(
    modifier: Modifier = Modifier,
    shape: Shape = rememberSciFiPanelShape(),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(HudPanelTop, HudPanelBottom),
                ),
            )
            .border(1.dp, HudStroke, shape)
            .padding(1.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .border(1.dp, HudInnerStroke.copy(alpha = 0.35f), shape),
        ) {
            content()
        }
    }
}

private fun MapHudUiState.toDisplayState(): MapHudDisplayState =
    when (this) {
        is MapHudUiState.Content -> {
            MapHudDisplayState(
                heroInitial = heroInitial,
                levelLabel = levelLabel,
                xpInLevel = xpInLevel,
                xpToNextLevel = xpToNextLevel,
                resources = resources,
            )
        }

        MapHudUiState.Loading,
        MapHudUiState.Unavailable -> {
            MapHudDisplayState(
                heroInitial = "?",
                levelLabel = "Lv --",
                xpInLevel = 0,
                xpToNextLevel = 1,
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
    val xpInLevel: Long,
    val xpToNextLevel: Long,
    val resources: List<MapHudResourceUiModel>,
)

@Composable
@PreviewLightDark
private fun MapPlayerHudDefaultPreview() {
    MaterialTheme {
        Box(modifier = Modifier.background(Color(0xFF070B0F)).padding(16.dp)) {
            MapPlayerHud(
                state = MapHudUiState.Content(
                    heroInitial = "A",
                    levelLabel = "Lv 7",
                    xpInLevel = 4_850,
                    xpToNextLevel = 7_500,
                    resources = listOf(
                        MapHudResourceUiModel(ResourceType.SCRAP, amount = 18, amountLabel = "18"),
                        MapHudResourceUiModel(ResourceType.COMPONENTS, amount = 7, amountLabel = "7"),
                        MapHudResourceUiModel(ResourceType.FUEL, amount = 75, amountLabel = "75"),
                    ),
                ),
                onHeroClick = {},
                onSettingsClick = {},
            )
        }
    }
}

@Composable
@PreviewLightDark
private fun MapPlayerHudLargeNumbersPreview() {
    MaterialTheme {
        Box(modifier = Modifier.background(Color(0xFF070B0F)).padding(16.dp)) {
            MapPlayerHud(
                state = MapHudUiState.Content(
                    heroInitial = "A",
                    levelLabel = "Lv 12",
                    xpInLevel = 12_400,
                    xpToNextLevel = 18_000,
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
}
