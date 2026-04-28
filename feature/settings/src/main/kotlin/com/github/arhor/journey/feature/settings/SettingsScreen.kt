package com.github.arhor.journey.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.arhor.journey.core.ui.components.ErrorMessage
import com.github.arhor.journey.core.ui.components.LoadingIndicator

@Composable
fun SettingsScreen(
    state: SettingsUiState,
) {
    when (state) {
        is SettingsUiState.Loading -> LoadingIndicator()
        is SettingsUiState.Failure -> ErrorMessage(state.errorMessage)
        is SettingsUiState.Content -> SettingsContent()
    }
}

@Composable
internal fun SettingsContent() {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            MapCreditsCard()
        }
    }
}

@Composable
private fun MapCreditsCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_map_credits_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.settings_map_credits_maplibre),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.settings_map_credits_openfreemap),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
