package com.github.arhor.journey.ui.views.settings

import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.DistanceUnit
import com.github.arhor.journey.domain.model.Resource
import com.github.arhor.journey.domain.usecase.ObserveSettingsUseCase
import com.github.arhor.journey.domain.usecase.SetDistanceUnitUseCase
import com.github.arhor.journey.test.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initialize should expose selected distance unit when observeSettings emits`() = runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val settingsFlow = MutableSharedFlow<Resource<AppSettings>>(replay = 1).apply {
            tryEmit(Resource.Success(AppSettings(distanceUnit = DistanceUnit.IMPERIAL)))
        }
        val observeSettingsUseCase = mockk<ObserveSettingsUseCase>()
        val setDistanceUnitUseCase = mockk<SetDistanceUnitUseCase>()
        every { observeSettingsUseCase.invoke() } returns settingsFlow

        val vm = SettingsViewModel(
            observeSettings = observeSettingsUseCase,
            setDistanceUnit = setDistanceUnitUseCase,
        )
        backgroundScope.launch { vm.uiState.collect() }

        // When
        advanceUntilIdle()

        // Then
        val state = vm.uiState.first { it is SettingsUiState.Content } as SettingsUiState.Content
        state.distanceUnit shouldBe DistanceUnit.IMPERIAL
        state.isUpdating shouldBe false
    }

    @Test
    fun `select distance unit should invoke SetDistanceUnitUseCase when selection changes`() = runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val settingsFlow = MutableSharedFlow<Resource<AppSettings>>(replay = 1).apply {
            tryEmit(Resource.Success(AppSettings(distanceUnit = DistanceUnit.METRIC)))
        }
        val observeSettingsUseCase = mockk<ObserveSettingsUseCase>()
        val setDistanceUnitUseCase = mockk<SetDistanceUnitUseCase>()
        every { observeSettingsUseCase.invoke() } returns settingsFlow
        coEvery { setDistanceUnitUseCase.invoke(DistanceUnit.IMPERIAL) } returns Unit

        val vm = SettingsViewModel(
            observeSettings = observeSettingsUseCase,
            setDistanceUnit = setDistanceUnitUseCase,
        )
        backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        // When
        vm.dispatch(SettingsIntent.SelectDistanceUnit(DistanceUnit.IMPERIAL))
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { setDistanceUnitUseCase.invoke(DistanceUnit.IMPERIAL) }
        (vm.uiState.value as SettingsUiState.Content).isUpdating shouldBe false
    }

    @Test
    fun `select distance unit should emit error effect when persistence fails`() = runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val settingsFlow = MutableSharedFlow<Resource<AppSettings>>(replay = 1).apply {
            tryEmit(Resource.Success(AppSettings(distanceUnit = DistanceUnit.METRIC)))
        }
        val observeSettingsUseCase = mockk<ObserveSettingsUseCase>()
        val setDistanceUnitUseCase = mockk<SetDistanceUnitUseCase>()
        every { observeSettingsUseCase.invoke() } returns settingsFlow
        coEvery { setDistanceUnitUseCase.invoke(any()) } throws IllegalStateException("cannot save")

        val vm = SettingsViewModel(
            observeSettings = observeSettingsUseCase,
            setDistanceUnit = setDistanceUnitUseCase,
        )
        backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        val effect = async { vm.effects.first() }

        // When
        vm.dispatch(SettingsIntent.SelectDistanceUnit(DistanceUnit.IMPERIAL))
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { setDistanceUnitUseCase.invoke(DistanceUnit.IMPERIAL) }
        effect.await() shouldBe SettingsEffect.Error("cannot save")
    }
}
