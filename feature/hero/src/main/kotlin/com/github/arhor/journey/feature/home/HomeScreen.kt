package com.github.arhor.journey.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.arhor.journey.feature.home.components.LoadingIndicator
import com.github.arhor.journey.feature.home.components.StatRow

@Composable
fun HomeScreen(
    state: HomeUiState,
    dispatch: (HomeIntent) -> Unit,
) {
    when (state) {
        is HomeUiState.Loading -> LoadingIndicator()
        is HomeUiState.Failure -> HomeFailure(state = state)
        is HomeUiState.Content -> HomeContent(state = state, dispatch = dispatch)
    }
}

@Composable
internal fun HomeFailure(
    state: HomeUiState.Failure,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.errorMessage,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
internal fun HomeContent(
    state: HomeUiState.Content,
    dispatch: (HomeIntent) -> Unit,
) {
    val progress = if (state.xpToNextLevel == 0L) {
        0f
    } else {
        (state.xpInLevel.toFloat() / state.xpToNextLevel.toFloat()).coerceIn(0f, 1f)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_hero_summary_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = state.heroName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.home_hero_level_value, state.level),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_progress_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        R.string.home_progress_xp_value,
                        state.xpInLevel,
                        state.xpToNextLevel,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_stats_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                StatRow(label = stringResource(R.string.home_stat_strength), value = state.strength)
                StatRow(label = stringResource(R.string.home_stat_vitality), value = state.vitality)
                StatRow(label = stringResource(R.string.home_stat_dexterity), value = state.dexterity)
                StatRow(label = stringResource(R.string.home_stat_stamina), value = state.stamina)
            }
        }
    }
}
