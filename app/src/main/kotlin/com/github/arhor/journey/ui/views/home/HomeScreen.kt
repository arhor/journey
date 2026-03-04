package com.github.arhor.journey.ui.views.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.arhor.journey.ui.components.ErrorMessage
import com.github.arhor.journey.ui.components.LoadingIndicator

@Composable
fun HomeScreen(
    state: HomeUiState,
    dispatch: (HomeIntent) -> Unit,
) {
    when (state) {
        is HomeUiState.Loading -> LoadingIndicator()
        is HomeUiState.Failure -> ErrorMessage(state.errorMessage)
        is HomeUiState.Content -> HomeContent(state = state, dispatch = dispatch)
    }
}

@Composable
internal fun HomeContent(
    state: HomeUiState.Content,
    dispatch: (HomeIntent) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Home",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp),
        )
    }
}
