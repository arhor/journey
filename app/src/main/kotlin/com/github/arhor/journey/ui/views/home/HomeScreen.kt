package com.github.arhor.journey.ui.views.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.arhor.journey.R
import com.github.arhor.journey.domain.model.ActivityType
import com.github.arhor.journey.ui.components.LoadingIndicator

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
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_import_summary_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        R.string.home_import_summary_today_activities,
                        state.importedTodayActivities,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(
                        R.string.home_import_summary_today_steps,
                        state.importedTodaySteps,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
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

        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_log_activity_title),
                    style = MaterialTheme.typography.titleMedium,
                )

                Text(
                    text = stringResource(R.string.home_log_activity_type_label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )

                ActivityType.entries.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = type == state.selectedActivityType,
                                enabled = !state.isSubmitting,
                                role = Role.RadioButton,
                                onClick = { dispatch(HomeIntent.SelectActivityType(type)) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = type == state.selectedActivityType,
                            onClick = null,
                            enabled = !state.isSubmitting,
                        )
                        Text(
                            text = activityTypeLabel(type),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                OutlinedTextField(
                    value = state.durationMinutesInput,
                    onValueChange = { dispatch(HomeIntent.ChangeDurationMinutes(it)) },
                    label = { Text(stringResource(R.string.home_log_duration_label)) },
                    placeholder = { Text(stringResource(R.string.home_log_duration_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.isSubmitting,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                )

                Button(
                    onClick = { dispatch(HomeIntent.SubmitActivity) },
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (state.isSubmitting) {
                            stringResource(R.string.home_log_submit_loading)
                        } else {
                            stringResource(R.string.home_log_submit)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun activityTypeLabel(type: ActivityType): String =
    when (type) {
        ActivityType.WALK -> stringResource(R.string.home_activity_walk)
        ActivityType.RUN -> stringResource(R.string.home_activity_run)
        ActivityType.WORKOUT -> stringResource(R.string.home_activity_workout)
        ActivityType.STRETCHING -> stringResource(R.string.home_activity_stretching)
        ActivityType.REST -> stringResource(R.string.home_activity_rest)
    }
