package com.github.arhor.journey.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.arhor.journey.R
import com.github.arhor.journey.core.ui.components.ErrorMessage
import com.github.arhor.journey.core.ui.components.LoadingIndicator
import com.github.arhor.journey.domain.model.MapStyle

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    dispatch: (SettingsIntent) -> Unit,
) {
    when (state) {
        is SettingsUiState.Loading -> LoadingIndicator()
        is SettingsUiState.Failure -> ErrorMessage(state.errorMessage)
        is SettingsUiState.Content -> SettingsContent(
            state = state,
            dispatch = dispatch,
        )
    }
}

@Composable
internal fun SettingsContent(
    state: SettingsUiState.Content,
    dispatch: (SettingsIntent) -> Unit,
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
        MapStyleCard(
            mapStyles = state.mapStyles,
            selectedMapStyleId = state.selectedMapStyleId,
            onMapStyleSelected = { styleId ->
                dispatch(SettingsIntent.MapStyleSelected(styleId))
            },
        )
        MapCreditsCard()
    }
}

@Composable
private fun MapStyleCard(
    mapStyles: List<MapStyle>,
    selectedMapStyleId: String,
    onMapStyleSelected: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = stringResource(R.string.settings_map_style_title),
                style = MaterialTheme.typography.titleSmall,
            )
            mapStyles.forEachIndexed { index, style ->
                val isSelected = style.id == selectedMapStyleId
                ListItem(
                    modifier = Modifier.selectable(
                        selected = isSelected,
                        onClick = { onMapStyleSelected(style.id) },
                        role = Role.RadioButton,
                    ),
                    headlineContent = {
                        Text(text = style.name)
                    },
                    leadingContent = {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                        )
                    },
                )
                if (index < mapStyles.lastIndex) {
                    HorizontalDivider()
                }
            }
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
