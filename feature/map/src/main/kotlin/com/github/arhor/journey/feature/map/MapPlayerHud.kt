package com.github.arhor.journey.feature.map

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
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.core.ui.components.ResourceTypeIcon
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
private val HudGlow = Color(0xFF59A4D7)
private val HudAccent = Color(0xFF65696C)
private const val LevelBadgeScale = 1.5f
private val LevelBadgeBaseSize = 54.dp
private val TopStatusPanelOverlap = (-12).dp
private const val HeroShieldShoulderStretch = 1.2f
private const val HeroShieldFlankRiseFraction = 0.45f
private const val HeroShieldBottomRiseFraction = 0.12f
private const val HeroShieldBottomWidthFraction = 0.5f
private val StatusPanelInnerEdge = 10.dp

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
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TopStatusPanel(
            levelLabel = displayState.levelLabel,
            xpInLevel = displayState.xpInLevel,
            xpToNextLevel = displayState.xpToNextLevel,
            resources = displayState.resources,
            onHeroClick = onHeroClick,
            onSettingsClick = onSettingsClick,
        )

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
private fun rememberHeroButtonShape(
    cornerCut: Dp = 9.dp,
    crestInset: Dp = 12.dp,
    flankInset: Dp = 9.dp,
    pointDepth: Dp = 9.dp,
    shoulderFraction: Float = 0.65f,
    pointWidth: Dp = 9.dp,
): Shape {
    val density = LocalDensity.current

    return remember(density, cornerCut, crestInset, flankInset, pointDepth, shoulderFraction, pointWidth) {
        val cutPx = with(density) { cornerCut.toPx() }
        val crestInsetPx = with(density) { crestInset.toPx() }
        val flankInsetPx = with(density) { flankInset.toPx() }
        val pointDepthPx = with(density) { pointDepth.toPx() }
        val pointWidthPx = with(density) { pointWidth.toPx() }

        GenericShape { size, _ ->
            val w = size.width
            val h = size.height

            val topInset = crestInsetPx.coerceIn(0f, w / 2f)
            val flankInsetX = flankInsetPx.coerceIn(0f, w / 2f)
            val shoulderY = (h * shoulderFraction * HeroShieldShoulderStretch).coerceIn(cutPx, h - cutPx)
            val flankY = (h - pointDepthPx * HeroShieldFlankRiseFraction).coerceIn(shoulderY, h)
            val bottomY = (h - pointDepthPx * HeroShieldBottomRiseFraction).coerceIn(flankY, h)
            val bottomFlatHalfWidth = (pointWidthPx * HeroShieldBottomWidthFraction)
                .coerceIn(0f, (w / 2f) - flankInsetX)

            // Draw a shield silhouette with a flat crest, tapered sides, and a softened bottom point.
            moveTo(topInset, 0f)
            lineTo(w - cutPx, 0f)
            lineTo(w, cutPx)
            lineTo(w, shoulderY)
            lineTo(w - flankInsetX, flankY * 1.1f)
            lineTo(flankInsetX, flankY * 1.1f)
            lineTo(0f, shoulderY)
            lineTo(0f, cutPx)
            close()
        }
    }
}

@Composable
private fun rememberStatusPanelShape(
    cornerCut: Dp = 9.dp,
    leftInset1: Dp = 12.dp,
    leftInset2: Dp = 9.dp,
    notchTopFraction: Float = 0.65f,
    notchSlant: Dp = 9.dp,
): Shape {
    val density = LocalDensity.current

    return remember(density, cornerCut, leftInset1, leftInset2, notchTopFraction, notchSlant) {
        val cutPx = with(density) { cornerCut.toPx() }
        val topInsetPx = with(density) { leftInset1.toPx() }
        val innerInsetPx = with(density) { leftInset2.toPx() }
        val slantPx = with(density) { notchSlant.toPx() }
        val innerEdgePx = with(density) { StatusPanelInnerEdge.toPx() }

        GenericShape { size, _ ->
            val w = size.width
            val h = size.height

            val nTop = h * notchTopFraction

            moveTo(topInsetPx, 0f)
            lineTo(w - cutPx, 0f)
            lineTo(w, cutPx)
            lineTo(w, h - cutPx)
            lineTo(w - cutPx, h)
            lineTo(cutPx, h)
            lineTo(innerInsetPx, h - cutPx)
            lineTo(innerInsetPx, nTop + slantPx)
            lineTo(innerEdgePx, nTop)
            lineTo(innerEdgePx, cutPx)
            close()
        }
    }
}

@Composable
private fun TopStatusPanel(
    levelLabel: String,
    xpInLevel: Long,
    xpToNextLevel: Long,
    resources: List<MapHudResourceUiModel>,
    onHeroClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TopStatusPanelOverlap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LevelBadgeButton(
            levelLabel = levelLabel,
            onClick = onHeroClick,
            modifier = Modifier.zIndex(1f),
        )

        HudPanel(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 55.dp),
            shape = rememberStatusPanelShape(
                cornerCut = 4.dp,
                leftInset1 = 6.dp,
                leftInset2 = 5.dp,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                XpStrip(
                    xpInLevel = xpInLevel,
                    xpToNextLevel = xpToNextLevel,
                    modifier = Modifier.weight(1f),
                )
                resources.forEach { resource ->
                    CompactResourceChip(state = resource)
                }
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(30.dp)
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
}

@Composable
private fun LevelBadgeButton(
    levelLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val badgeSize = LevelBadgeBaseSize * LevelBadgeScale
    val contentDescriptionText = stringResource(R.string.map_hud_hero_content_description, levelLabel)
    val levelValue = levelLabel.filter(Char::isDigit).ifBlank { "--" }

    HudPanel(
        modifier = modifier.size(width = badgeSize, height = badgeSize),
        shape = rememberHeroButtonShape(
            cornerCut = 9.dp * LevelBadgeScale,
            crestInset = 12.dp * LevelBadgeScale,
            flankInset = 12.dp * LevelBadgeScale,
            pointDepth = 9.dp * LevelBadgeScale,
        ),
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(badgeSize)
                .semantics { contentDescription = contentDescriptionText }
                .testTag(MAP_HUD_HERO_BUTTON_TEST_TAG),
            contentPadding = PaddingValues(0.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "LVL",
                    color = HudMutedText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp * LevelBadgeScale),
                    letterSpacing = 1.sp * LevelBadgeScale,
                    maxLines = 1,
                )
                Text(
                    text = levelValue,
                    color = HudText,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp * LevelBadgeScale),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun XpStrip(
    xpInLevel: Long,
    xpToNextLevel: Long,
    modifier: Modifier = Modifier,
) {
    val safeTotal = xpToNextLevel.coerceAtLeast(1L)
    val progress = (xpInLevel.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = stringResource(
                R.string.map_hud_xp_label,
                xpInLevel.toInt(),
                xpToNextLevel.toInt(),
            ),
            color = HudText,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFF081018))
                .border(1.dp, HudStroke.copy(alpha = 0.6f), RoundedCornerShape(999.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF86E7FF), Color(0xFF3B93B4)),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun CompactResourceChip(
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
            .heightIn(min = 22.dp)
            .semantics { contentDescription = contentDescriptionText },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ResourceTypeIcon(
            resourceType = state.resourceType,
            modifier = Modifier.size(13.dp),
            contentDescription = null,
        )
        Text(
            text = state.amountLabel,
            color = HudText,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
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
    shape: Shape = rememberHeroButtonShape(),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        HudPanelTop.copy(alpha = 0.92f),
                        HudPanelBottom.copy(alpha = 0.96f),
                    ),
                ),
            )
            .hudPanelDecoration()
            .border(1.dp, HudStroke, shape)
            .padding(2.dp),
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

private fun Modifier.hudPanelDecoration(): Modifier = drawWithCache {
    val scanlineColor = Color.White.copy(alpha = 0.018f)
    val topGlow = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.07f),
            Color.Transparent,
            Color.Black.copy(alpha = 0.16f),
        ),
    )
    val redAccent = HudAccent.copy(alpha = 0.72f)
    val dimRedAccent = HudAccent.copy(alpha = 0.34f)

    onDrawWithContent {
        drawContent()

        var y = 0f
        val step = 5.dp.toPx()
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

        val inset = 2.dp.toPx()
        val corner = 8.dp.toPx()
        val shortEdge = size.width.coerceAtMost(44.dp.toPx())
        val stroke = 1.5.dp.toPx()

        drawLine(
            color = redAccent,
            start = Offset(size.width - shortEdge, inset),
            end = Offset(size.width - corner, inset),
            strokeWidth = stroke,
        )
        drawLine(
            color = redAccent,
            start = Offset(size.width - corner, inset),
            end = Offset(size.width - inset, corner),
            strokeWidth = stroke,
        )
        drawLine(
            color = redAccent,
            start = Offset(size.width - inset, corner),
            end = Offset(size.width - inset, size.height - corner),
            strokeWidth = stroke,
        )
        drawLine(
            color = dimRedAccent,
            start = Offset(size.width - corner, size.height - inset),
            end = Offset(size.width - shortEdge, size.height - inset),
            strokeWidth = stroke,
        )
    }
}

private fun MapHudUiState.toDisplayState(): MapHudDisplayState =
    when (this) {
        is MapHudUiState.Content -> {
            MapHudDisplayState(
                levelLabel = levelLabel,
                xpInLevel = xpInLevel,
                xpToNextLevel = xpToNextLevel,
                resources = resources,
            )
        }

        MapHudUiState.Loading,
        MapHudUiState.Unavailable -> {
            MapHudDisplayState(
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
