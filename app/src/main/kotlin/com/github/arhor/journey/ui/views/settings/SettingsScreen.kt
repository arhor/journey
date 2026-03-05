package com.github.arhor.journey.ui.views.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.arhor.journey.R
import com.github.arhor.journey.domain.model.DistanceUnit
import com.github.arhor.journey.ui.components.ErrorMessage
import com.github.arhor.journey.ui.components.LoadingIndicator

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    dispatch: (SettingsIntent) -> Unit,
) {
    when (state) {
        is SettingsUiState.Loading -> LoadingIndicator()
        is SettingsUiState.Failure -> ErrorMessage(state.errorMessage)
        is SettingsUiState.Content -> SettingsContent(state = state, dispatch = dispatch)
    }
}

@Composable
internal fun SettingsContent(
    state: SettingsUiState.Content,
    dispatch: (SettingsIntent) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.settings_distance_unit_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            DistanceUnit.entries.forEach { unit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.distanceUnit == unit,
                            enabled = !state.isUpdating,
                            role = Role.RadioButton,
                            onClick = { dispatch(SettingsIntent.SelectDistanceUnit(unit)) },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.distanceUnit == unit,
                        onClick = null,
                        enabled = !state.isUpdating,
                    )
                    Text(
                        text = distanceUnitLabel(unit),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            if (state.isUpdating) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.settings_updating),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Text(
                text = stringResource(R.string.settings_health_connect_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_health_connect_data_usage),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.settings_health_connect_import_scope),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = healthConnectStatusLabel(state),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { dispatch(SettingsIntent.ConnectHealthConnect) },
                enabled = state.healthConnectConnectionStatus != HealthConnectConnectionStatus.CONNECTING,
            ) {
                Text(text = stringResource(R.string.settings_health_connect_connect_action))
            }
        }
    }
}

@Composable
private fun distanceUnitLabel(unit: DistanceUnit): String =
    when (unit) {
        DistanceUnit.METRIC -> stringResource(R.string.settings_distance_unit_metric)
        DistanceUnit.IMPERIAL -> stringResource(R.string.settings_distance_unit_imperial)
    }

@Composable
private fun healthConnectStatusLabel(state: SettingsUiState.Content): String =
    when {
        state.healthConnectConnectionStatus == HealthConnectConnectionStatus.CONNECTED -> {
            stringResource(R.string.settings_health_connect_status_connected)
        }

        state.healthConnectPermissionStatus == HealthConnectPermissionStatus.REQUESTING -> {
            stringResource(R.string.settings_health_connect_status_requesting)
        }

        state.healthConnectPermissionStatus == HealthConnectPermissionStatus.DENIED -> {
            stringResource(
                R.string.settings_health_connect_status_denied,
                state.missingHealthConnectPermissions.size,
            )
        }

        else -> stringResource(R.string.settings_health_connect_status_disconnected)
    }
