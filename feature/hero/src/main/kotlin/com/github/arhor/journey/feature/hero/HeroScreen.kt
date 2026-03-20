package com.github.arhor.journey.feature.hero

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
import com.github.arhor.journey.feature.hero.components.LoadingIndicator
import com.github.arhor.journey.feature.hero.components.ResourceRow

@Composable
fun HeroScreen(
    state: HeroUiState,
    dispatch: (HeroIntent) -> Unit,
) {
    when (state) {
        is HeroUiState.Loading -> LoadingIndicator()
        is HeroUiState.Failure -> HeroFailure(state = state)
        is HeroUiState.Content -> HeroContent(state = state, dispatch = dispatch)
    }
}

@Composable
internal fun HeroFailure(
    state: HeroUiState.Failure,
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
internal fun HeroContent(
    state: HeroUiState.Content,
    dispatch: (HeroIntent) -> Unit,
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
                    text = stringResource(R.string.hero_hero_summary_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = state.heroName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.hero_hero_level_value, state.level),
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
                    text = stringResource(R.string.hero_progress_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        R.string.hero_progress_xp_value,
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
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.hero_resources_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                state.resources.forEach {
                    ResourceRow(
                        resourceType = it.resourceType,
                        amount = it.amount,
                    )
                }
            }
        }
    }
}
