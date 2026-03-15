package com.github.arhor.journey.core.ui

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test

class MviViewModelTest {

    @Test
    fun `dispatch should forward intent and update ui state when intent is received`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        lateinit var viewModel: TestMviViewModel
        try {
            viewModel = TestMviViewModel()
            viewModel.uiState
            advanceUntilIdle()

            // When
            viewModel.dispatch(7)
            advanceUntilIdle()

            // Then
            viewModel.handledIntents shouldBe listOf(7)
            viewModel.uiState.value shouldBe 7
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `emitEffect should emit value when view model publishes effect`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        lateinit var viewModel: TestMviViewModel
        try {
            viewModel = TestMviViewModel()
            val effectDeferred = async { viewModel.effects.first() }
            runCurrent()

            // When
            viewModel.publishEffect("handled:3")
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe "handled:3"
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class TestMviViewModel : MviViewModel<Int, String, Int>(
        initialState = -1,
        started = SharingStarted.Eagerly,
    ) {
        private val state = MutableStateFlow(0)
        val handledIntents: MutableList<Int> = mutableListOf()

        override fun buildUiState(): Flow<Int> = state

        override suspend fun handleIntent(intent: Int) {
            handledIntents += intent
            state.value = intent
        }

        fun publishEffect(effect: String) {
            emitEffect(effect)
        }
    }
}
