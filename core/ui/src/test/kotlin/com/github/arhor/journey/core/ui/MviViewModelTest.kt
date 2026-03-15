package com.github.arhor.journey.core.ui

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
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

    @Test
    fun `uiState should be initialized lazily when it is not accessed`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        lateinit var viewModel: LazyUiStateViewModel
        try {
            viewModel = LazyUiStateViewModel()

            // When
            advanceUntilIdle()

            // Then
            viewModel.buildUiStateCalls shouldBe 0
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `dispatch should preserve intent order when multiple intents are sent quickly`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        lateinit var viewModel: TestMviViewModel
        try {
            viewModel = TestMviViewModel()
            viewModel.uiState
            advanceUntilIdle()

            // When
            viewModel.dispatch(1)
            viewModel.dispatch(2)
            viewModel.dispatch(3)
            advanceUntilIdle()

            // Then
            viewModel.handledIntents shouldBe listOf(1, 2, 3)
            viewModel.uiState.value shouldBe 3
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `dispatch should enqueue intent through fallback path when intent buffer is full`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        lateinit var viewModel: BlockingIntentViewModel
        try {
            viewModel = BlockingIntentViewModel()
            viewModel.uiState
            advanceUntilIdle()

            // When
            viewModel.dispatch(1)
            runCurrent()
            viewModel.dispatch(2)
            runCurrent()
            viewModel.releaseFirstIntent()
            advanceUntilIdle()

            // Then
            viewModel.handledIntents shouldBe listOf(1, 2)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `dispatch should drop intent when collector has not started yet`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        lateinit var viewModel: LazyUiStateViewModel
        try {
            viewModel = LazyUiStateViewModel()

            // When
            viewModel.dispatch(5)
            runCurrent()
            advanceUntilIdle()

            // Then
            viewModel.handledIntents shouldBe emptyList()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `emitEffect should enqueue through fallback path when collector is temporarily blocked`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        lateinit var viewModel: ZeroBufferEffectsViewModel
        try {
            viewModel = ZeroBufferEffectsViewModel()
            val firstEffectConsumed = CompletableDeferred<Unit>()
            val effectsDeferred = async {
                viewModel.effects
                    .onEach { effect ->
                        if (effect == "handled:1") {
                            firstEffectConsumed.await()
                        }
                    }
                    .take(2)
                    .toList()
            }
            runCurrent()

            // When
            viewModel.publishEffect(1)
            runCurrent()
            viewModel.publishEffect(2)
            runCurrent()
            firstEffectConsumed.complete(Unit)
            advanceUntilIdle()

            // Then
            effectsDeferred.await() shouldBe listOf("handled:1", "handled:2")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `uiState should keep initial value when upstream flow never emits`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        lateinit var viewModel: NeverEmittingStateViewModel
        try {
            viewModel = NeverEmittingStateViewModel(initialState = -1)

            // When
            val currentState = viewModel.uiState.value

            // Then
            currentState shouldBe -1
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

    private class LazyUiStateViewModel : MviViewModel<Int, String, Int>(
        initialState = -1,
        started = SharingStarted.Eagerly,
    ) {
        private val state = MutableStateFlow(0)
        var buildUiStateCalls: Int = 0
            private set
        val handledIntents: MutableList<Int> = mutableListOf()

        override fun buildUiState(): Flow<Int> {
            buildUiStateCalls += 1
            return state
        }

        override suspend fun handleIntent(intent: Int) {
            handledIntents += intent
            state.value = intent
        }
    }

    private class BlockingIntentViewModel : MviViewModel<Int, String, Int>(
        initialState = -1,
        started = SharingStarted.Eagerly,
        intentsBuffer = 0,
    ) {
        private val state = MutableStateFlow(0)
        private val firstIntentGate = CompletableDeferred<Unit>()
        val handledIntents: MutableList<Int> = mutableListOf()

        override fun buildUiState(): Flow<Int> = state

        override suspend fun handleIntent(intent: Int) {
            handledIntents += intent
            state.value = intent
            if (intent == 1) {
                firstIntentGate.await()
            }
        }

        fun releaseFirstIntent() {
            firstIntentGate.complete(Unit)
        }
    }

    private class ZeroBufferEffectsViewModel : MviViewModel<Int, String, Int>(
        initialState = -1,
        started = SharingStarted.Eagerly,
        effectsBuffer = 0,
    ) {
        override fun buildUiState(): Flow<Int> = MutableStateFlow(0)

        override suspend fun handleIntent(intent: Int) = Unit

        fun publishEffect(value: Int) {
            emitEffect("handled:$value")
        }
    }

    private class NeverEmittingStateViewModel(
        initialState: Int,
    ) : MviViewModel<Int, String, Int>(
        initialState = initialState,
        started = SharingStarted.Eagerly,
    ) {
        override fun buildUiState(): Flow<Int> = emptyFlow()

        override suspend fun handleIntent(intent: Int) = Unit
    }
}
