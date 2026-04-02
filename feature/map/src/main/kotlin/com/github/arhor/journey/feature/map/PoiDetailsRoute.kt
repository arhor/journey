package com.github.arhor.journey.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PoiDetailsRoute(
    onBack: () -> Unit,
    onOpenMiniGame: () -> Unit,
    vm: PoiDetailsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    PoiDetailsScreen(
        state = state,
        onBack = onBack,
        onOpenMiniGame = onOpenMiniGame,
    )
}
