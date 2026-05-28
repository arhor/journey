package com.github.arhor.journey.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.github.arhor.journey.R
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal const val BREACH_DIRECTIONAL_GUIDANCE_FLOATING_ARROW_TEST_TAG =
    "breach_directional_guidance_floating_arrow"
internal const val BREACH_DIRECTIONAL_GUIDANCE_ON_TARGET_TEST_TAG =
    "breach_directional_guidance_on_target"

@Composable
internal fun BreachProtocolOverlay(
    state: BreachProtocolUiState,
    breachGuidance: BreachDirectionalGuidanceUiState,
    dispatch: (MapIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        BreachGuidanceHud(guidance = breachGuidance)

        BreachProtocolPanel(
            state = state,
            dispatch = dispatch,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
        )
    }
}

@Composable
private fun BoxScope.BreachGuidanceHud(
    guidance: BreachDirectionalGuidanceUiState,
) {
    when (guidance) {
        BreachDirectionalGuidanceUiState.Hidden -> Unit

        is BreachDirectionalGuidanceUiState.Unavailable -> GuidanceUnavailableMessage(
            message = guidance.message,
        )

        is BreachDirectionalGuidanceUiState.FloatingArrow -> FloatingGuidanceArrow(
            bearingDegrees = guidance.bearingDegrees,
        )

        is BreachDirectionalGuidanceUiState.OnTarget -> OnTargetGuidanceMarker()
    }
}

@Composable
private fun BoxScope.FloatingGuidanceArrow(
    bearingDegrees: Double,
) {
    val distancePx = with(LocalDensity.current) { 96.dp.toPx() }
    val radians = Math.toRadians(bearingDegrees)
    val xOffset = (sin(radians) * distancePx).roundToInt()
    val yOffset = (-cos(radians) * distancePx).roundToInt()

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .offset { IntOffset(xOffset, yOffset) }
            .testTag(BREACH_DIRECTIONAL_GUIDANCE_FLOATING_ARROW_TEST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer(rotationZ = bearingDegrees.toFloat()),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "▲",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.OnTargetGuidanceMarker() {
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .testTag(BREACH_DIRECTIONAL_GUIDANCE_ON_TARGET_TEST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 8.dp,
        ) {}
    }
}

@Composable
private fun BoxScope.GuidanceUnavailableMessage(
    message: String,
) {
    Surface(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 24.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BreachProtocolPanel(
    state: BreachProtocolUiState,
    dispatch: (MapIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        BreachProtocolUiState.Idle -> {
            Button(
                modifier = modifier,
                onClick = { dispatch(MapIntent.PulseClicked) },
            ) {
                Text(text = stringResource(R.string.breach_pulse_button))
            }
        }

        BreachProtocolUiState.Scanning -> {
            TextPanel(
                text = stringResource(R.string.breach_scanning),
                modifier = modifier,
            )
        }

        is BreachProtocolUiState.SignalLocked -> {
            SignalPanel(
                state = state,
                dispatch = dispatch,
                modifier = modifier,
            )
        }

        is BreachProtocolUiState.Uploading -> {
            UploadPanel(
                state = state,
                dispatch = dispatch,
                modifier = modifier,
            )
        }

        is BreachProtocolUiState.Completed -> {
            CompletedPanel(
                state = state,
                dispatch = dispatch,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun TextPanel(
    text: String,
    modifier: Modifier = Modifier,
) {
    PanelSurface(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SignalPanel(
    state: BreachProtocolUiState.SignalLocked,
    dispatch: (MapIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    PanelSurface(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = state.districtName,
                style = MaterialTheme.typography.titleMedium,
            )
            state.distanceMeters?.let { distanceMeters ->
                Text(
                    text = stringResource(R.string.breach_distance_meters, distanceMeters),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.disabledReason != null) {
                Text(
                    text = state.disabledReason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { dispatch(MapIntent.StartBreachUpload) },
                    enabled = state.canStartUpload,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.breach_start_upload))
                }
                OutlinedButton(
                    onClick = { dispatch(MapIntent.DismissBreachPanel) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.breach_dismiss_button))
                }
            }
        }
    }
}

@Composable
private fun UploadPanel(
    state: BreachProtocolUiState.Uploading,
    dispatch: (MapIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    PanelSurface(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = state.districtName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.breach_upload_progress, state.progressPercent),
                style = MaterialTheme.typography.bodyLarge,
            )
            LinearProgressIndicator(
                progress = { state.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { dispatch(MapIntent.BreachUploadTick) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.breach_upload_continue))
                }
                OutlinedButton(
                    onClick = { dispatch(MapIntent.CancelBreachUpload) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.breach_cancel_upload))
                }
            }
        }
    }
}

@Composable
private fun CompletedPanel(
    state: BreachProtocolUiState.Completed,
    dispatch: (MapIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    PanelSurface(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.breach_complete_message, state.districtName),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = { dispatch(MapIntent.DismissBreachPanel) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.breach_dismiss_button))
            }
        }
    }
}

@Composable
private fun PanelSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}
