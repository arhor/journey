package com.github.arhor.journey.feature.map

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfig
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus

@Immutable
data class MapDebugUiState(
    val isSheetVisible: Boolean,
    val enabledInfoItems: Set<MapDebugInfoItem>,
    val isTilesGridOverlayEnabled: Boolean,
    val canonicalZoom: Int,
    val revealRadiusMeters: Int,
    val renderMode: MapRenderMode,
)

enum class MapDebugInfoItem {
    FogZoom,
    VisibleTiles,
    ExploredHere,
    TrackingStatus,
}

enum class MapRenderMode {
    Standard,
    Debug,
}

@Composable
internal fun MapDebugControlsSheet(
    state: MapUiState.Content,
    dispatch: (MapIntent) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = {
            dispatch(MapIntent.DebugControlsDismissed)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.map_debug_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            DebugSectionCard(
                title = stringResource(R.string.map_debug_visible_on_map_title),
            ) {
                MapDebugInfoItem.entries.forEach { item ->
                    DebugSwitchRow(
                        label = stringResource(item.labelRes()),
                        checked = item in state.debug.enabledInfoItems,
                        onCheckedChange = { isChecked ->
                            dispatch(
                                MapIntent.DebugInfoVisibilityChanged(
                                    item = item,
                                    isVisible = isChecked,
                                ),
                            )
                        },
                    )
                }
            }

            DebugSectionCard(
                title = stringResource(R.string.map_debug_rendering_title),
            ) {
                DebugSwitchRow(
                    label = stringResource(R.string.map_debug_fog_overlay_label),
                    checked = state.fogOfWar.isOverlayEnabled,
                    onCheckedChange = { isChecked ->
                        dispatch(MapIntent.FogOfWarOverlayToggled(isEnabled = isChecked))
                    },
                )

                DebugSwitchRow(
                    label = stringResource(R.string.map_debug_tiles_grid_label),
                    checked = state.debug.isTilesGridOverlayEnabled,
                    onCheckedChange = { isChecked ->
                        dispatch(MapIntent.TilesGridOverlayToggled(isEnabled = isChecked))
                    },
                )

                Text(
                    text = stringResource(R.string.map_debug_render_mode_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )

                MapRenderMode.entries.forEach { mode ->
                    DebugRadioRow(
                        label = stringResource(mode.labelRes()),
                        selected = state.debug.renderMode == mode,
                        onClick = {
                            dispatch(MapIntent.MapRenderModeSelected(mode = mode))
                        },
                    )
                }
            }

            DebugSectionCard(
                title = stringResource(R.string.map_debug_exploration_prototype_title),
            ) {
                DebugStepperRow(
                    label = stringResource(R.string.map_debug_canonical_zoom_label),
                    value = state.debug.canonicalZoom.toString(),
                    onDecrement = {
                        dispatch(
                            MapIntent.CanonicalZoomChanged(
                                value = state.debug.canonicalZoom - DEBUG_STEPPER_STEP,
                            ),
                        )
                    },
                    onIncrement = {
                        dispatch(
                            MapIntent.CanonicalZoomChanged(
                                value = state.debug.canonicalZoom + DEBUG_STEPPER_STEP,
                            ),
                        )
                    },
                    decrementEnabled = state.debug.canonicalZoom > ExplorationTileRuntimeConfig.MIN_CANONICAL_ZOOM,
                    incrementEnabled = state.debug.canonicalZoom < ExplorationTileRuntimeConfig.MAX_CANONICAL_ZOOM,
                )

                DebugStepperRow(
                    label = stringResource(R.string.map_debug_reveal_radius_label),
                    value = stringResource(
                        R.string.map_debug_reveal_radius_value,
                        state.debug.revealRadiusMeters,
                    ),
                    onDecrement = {
                        dispatch(
                            MapIntent.RevealRadiusMetersChanged(
                                value = state.debug.revealRadiusMeters - DEBUG_STEPPER_STEP,
                            ),
                        )
                    },
                    onIncrement = {
                        dispatch(
                            MapIntent.RevealRadiusMetersChanged(
                                value = state.debug.revealRadiusMeters + DEBUG_STEPPER_STEP,
                            ),
                        )
                    },
                    decrementEnabled = state.debug.revealRadiusMeters >
                        ExplorationTileRuntimeConfig.MIN_REVEAL_RADIUS_METERS.toInt(),
                    incrementEnabled = true,
                )
            }

            DebugSectionCard(
                title = stringResource(R.string.map_debug_actions_title),
            ) {
                Text(
                    text = stringResource(R.string.map_tracking_panel_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(state.trackingStatusMessageRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            dispatch(
                                if (state.isExplorationTrackingActive) {
                                    MapIntent.StopTrackingClicked
                                } else {
                                    MapIntent.ResumeTrackingClicked
                                },
                            )
                        },
                    ) {
                        Text(
                            text = stringResource(
                                if (state.isExplorationTrackingActive) {
                                    R.string.map_tracking_stop_button_label
                                } else {
                                    R.string.map_tracking_resume_button_label
                                },
                            ),
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            dispatch(MapIntent.ResetExploredTilesClicked)
                        },
                    ) {
                        Text(text = stringResource(R.string.map_reset_fog_button_label))
                    }
                }
            }
        }
    }
}

@Composable
internal fun MapDebugInfoOverlay(
    state: MapUiState.Content,
    modifier: Modifier = Modifier,
) {
    if (state.debug.enabledInfoItems.isEmpty()) {
        return
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MapDebugInfoItem.entries.forEach { item ->
                if (item !in state.debug.enabledInfoItems) {
                    return@forEach
                }

                Text(
                    text = when (item) {
                        MapDebugInfoItem.FogZoom -> {
                            stringResource(
                                R.string.map_debug_info_fog_zoom_value,
                                state.fogOfWar.canonicalZoom,
                            )
                        }

                        MapDebugInfoItem.VisibleTiles -> {
                            stringResource(
                                R.string.map_debug_info_visible_tiles_value,
                                state.fogOfWar.visibleTileCount,
                            )
                        }

                        MapDebugInfoItem.ExploredHere -> {
                            stringResource(
                                R.string.map_debug_info_explored_here_value,
                                state.fogOfWar.exploredVisibleTileCount,
                            )
                        }

                        MapDebugInfoItem.TrackingStatus -> {
                            stringResource(
                                R.string.map_debug_info_tracking_status_value,
                                stringResource(state.trackingStatusMessageRes()),
                            )
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DebugStepperRow(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementEnabled: Boolean,
    incrementEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onDecrement,
                enabled = decrementEnabled,
            ) {
                Text(text = "-")
            }
            OutlinedButton(
                onClick = onIncrement,
                enabled = incrementEnabled,
            ) {
                Text(text = "+")
            }
        }
    }
}

@Composable
private fun DebugSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            content()
        }
    }
}

@Composable
private fun DebugSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = 16.dp),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun DebugRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@StringRes
private fun MapDebugInfoItem.labelRes(): Int =
    when (this) {
        MapDebugInfoItem.FogZoom -> R.string.map_debug_info_item_fog_zoom
        MapDebugInfoItem.VisibleTiles -> R.string.map_debug_info_item_visible_tiles
        MapDebugInfoItem.ExploredHere -> R.string.map_debug_info_item_explored_here
        MapDebugInfoItem.TrackingStatus -> R.string.map_debug_info_item_tracking_status
    }

@StringRes
private fun MapRenderMode.labelRes(): Int =
    when (this) {
        MapRenderMode.Standard -> R.string.map_debug_render_mode_standard
        MapRenderMode.Debug -> R.string.map_debug_render_mode_debug
}

@StringRes
internal fun MapUiState.Content.trackingStatusMessageRes(): Int {
    return when (explorationTrackingStatus) {
        ExplorationTrackingStatus.INACTIVE -> R.string.map_tracking_status_inactive
        ExplorationTrackingStatus.STARTING -> R.string.map_tracking_status_starting
        ExplorationTrackingStatus.TRACKING -> R.string.map_tracking_status_active
        ExplorationTrackingStatus.PERMISSION_DENIED -> R.string.map_tracking_status_permission_denied
        ExplorationTrackingStatus.LOCATION_SERVICES_DISABLED -> R.string.map_tracking_status_location_disabled
        ExplorationTrackingStatus.TEMPORARILY_UNAVAILABLE -> R.string.map_tracking_status_waiting
    }
}

private const val DEBUG_STEPPER_STEP = 1
