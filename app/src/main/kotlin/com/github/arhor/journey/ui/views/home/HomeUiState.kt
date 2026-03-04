package com.github.arhor.journey.ui.views.home

import androidx.compose.runtime.Immutable

sealed interface HomeUiState {

    @Immutable
    data object Loading : HomeUiState

    @Immutable
    data class Failure(val errorMessage: String) : HomeUiState

    @Immutable
    data object Content : HomeUiState
}
