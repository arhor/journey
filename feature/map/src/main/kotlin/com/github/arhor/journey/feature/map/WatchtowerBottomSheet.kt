package com.github.arhor.journey.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WatchtowerBottomSheet(
    state: WatchtowerSheetUiState,
    onDismiss: () -> Unit,
    onClaim: () -> Unit,
    onUpgrade: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(
                    when (state.phase) {
                        WatchtowerSheetPhase.DISCOVERED_DORMANT -> R.string.watchtower_status_dormant
                        WatchtowerSheetPhase.CLAIMED -> R.string.watchtower_status_claimed
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            state.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.distanceMeters?.let { distanceMeters ->
                WatchtowerFact(
                    label = stringResource(R.string.watchtower_distance_label),
                    value = stringResource(R.string.watchtower_distance_value, distanceMeters),
                )
            }
            state.level?.let { level ->
                WatchtowerFact(
                    label = stringResource(R.string.watchtower_level_label),
                    value = stringResource(R.string.watchtower_level_value, level),
                )
            }
            state.revealRadiusMeters?.let { revealRadiusMeters ->
                WatchtowerFact(
                    label = stringResource(R.string.watchtower_reveal_radius_label),
                    value = stringResource(R.string.watchtower_radius_value, revealRadiusMeters),
                )
            }
            state.nextRevealRadiusMeters?.let { nextRevealRadiusMeters ->
                WatchtowerFact(
                    label = stringResource(R.string.watchtower_next_reveal_radius_label),
                    value = stringResource(R.string.watchtower_radius_value, nextRevealRadiusMeters),
                )
            }
            state.claimCostLabel
                ?.takeIf { state.phase == WatchtowerSheetPhase.DISCOVERED_DORMANT }
                ?.let { claimCostLabel ->
                    WatchtowerFact(
                        label = stringResource(R.string.watchtower_claim_cost_label),
                        value = claimCostLabel,
                    )
                }
            state.upgradeCostLabel
                ?.takeIf { state.phase == WatchtowerSheetPhase.CLAIMED && !state.isAtMaxLevel }
                ?.let { upgradeCostLabel ->
                    WatchtowerFact(
                        label = stringResource(R.string.watchtower_upgrade_cost_label),
                        value = upgradeCostLabel,
                    )
                }

            when (state.phase) {
                WatchtowerSheetPhase.DISCOVERED_DORMANT -> {
                    Button(
                        onClick = onClaim,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.canClaim,
                    ) {
                        Text(text = stringResource(R.string.watchtower_claim_button))
                    }
                    state.claimDisabledReason
                        ?.takeIf { !state.canClaim }
                        ?.let { reason ->
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                }

                WatchtowerSheetPhase.CLAIMED -> {
                    if (state.isAtMaxLevel) {
                        Text(
                            text = stringResource(R.string.watchtower_max_level_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Button(
                            onClick = onUpgrade,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.canUpgrade,
                        ) {
                            Text(text = stringResource(R.string.watchtower_upgrade_button))
                        }
                        state.upgradeDisabledReason
                            ?.takeIf { !state.canUpgrade }
                            ?.let { reason ->
                                Text(
                                    text = reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun WatchtowerFact(
    label: String,
    value: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
