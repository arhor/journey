package com.github.arhor.journey.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.arhor.journey.R

@Composable
internal fun BreachProtocolOverlay(
    state: BreachProtocolUiState,
    dispatch: (MapIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            BreachProtocolUiState.Idle -> {
                Button(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                    onClick = { dispatch(MapIntent.PulseClicked) },
                ) {
                    Text(text = stringResource(R.string.breach_pulse_button))
                }
            }

            BreachProtocolUiState.Scanning -> {
                TextPanel(
                    text = stringResource(R.string.breach_scanning),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                )
            }

            is BreachProtocolUiState.SignalLocked -> {
                SignalPanel(
                    state = state,
                    dispatch = dispatch,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                )
            }

            is BreachProtocolUiState.Uploading -> {
                UploadPanel(
                    state = state,
                    dispatch = dispatch,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                )
            }

            is BreachProtocolUiState.Completed -> {
                CompletedPanel(
                    state = state,
                    dispatch = dispatch,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                )
            }
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
