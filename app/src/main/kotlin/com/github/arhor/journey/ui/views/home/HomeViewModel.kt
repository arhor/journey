package com.github.arhor.journey.ui.views.home

import androidx.compose.runtime.Stable
import com.github.arhor.journey.ui.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@Stable
@HiltViewModel
class HomeViewModel @Inject constructor() : MviViewModel<HomeUiState, HomeEffect, HomeIntent>(
    initialState = HomeUiState.Content,
) {
    override suspend fun handleIntent(intent: HomeIntent) {

    }
}
