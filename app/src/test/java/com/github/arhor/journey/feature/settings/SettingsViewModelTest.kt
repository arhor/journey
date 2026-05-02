package com.github.arhor.journey.feature.settings

import androidx.lifecycle.viewModelScope
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.error.AppSettingsError
import com.github.arhor.journey.domain.repository.AppSettingsRepository
import com.github.arhor.journey.domain.usecase.ObserveAvailableMapStylesUseCase
import com.github.arhor.journey.domain.usecase.ObserveSelectedMapStyleUseCase
import com.github.arhor.journey.domain.usecase.SetSelectedMapStyleUseCase
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test

class SettingsViewModelTest {

    @Test
    fun `uiState should expose map style options and selected style`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val repository = FakeAppSettingsRepository(
            selectedStyle = MapStyle.styleById("light")!!,
        )
        val viewModel = createViewModel(repository)

        try {
            // When
            val actual = viewModel.awaitContent()

            // Then
            actual.mapStyles shouldContainExactly MapStyle.availableStyles
            actual.selectedMapStyleId shouldBe "light"
        } finally {
            tearDownMainDispatcher(viewModel)
        }
    }

    @Test
    fun `dispatch should save selected map style when map style is selected`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val repository = FakeAppSettingsRepository()
        val viewModel = createViewModel(repository)

        try {
            viewModel.awaitContent()

            // When
            viewModel.dispatch(SettingsIntent.MapStyleSelected("urban-noir"))
            advanceUntilIdle()

            // Then
            repository.savedStyleIds shouldBe listOf("urban-noir")
            viewModel.awaitContent { it.selectedMapStyleId == "urban-noir" }
        } finally {
            tearDownMainDispatcher(viewModel)
        }
    }

    @Test
    fun `dispatch should emit error effect when selected map style cannot be saved`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        val repository = FakeAppSettingsRepository(
            saveResult = Output.Failure(
                AppSettingsError.SavingFailed(message = "Could not save style"),
            ),
        )
        val viewModel = createViewModel(repository)
        val effect = async { viewModel.effects.first() }

        try {
            viewModel.awaitContent()

            // When
            viewModel.dispatch(SettingsIntent.MapStyleSelected("light"))
            advanceUntilIdle()

            // Then
            effect.await() shouldBe SettingsEffect.Error("Could not save style")
        } finally {
            tearDownMainDispatcher(viewModel)
        }
    }

    private fun TestScope.tearDownMainDispatcher(viewModel: SettingsViewModel) {
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        repository: AppSettingsRepository,
    ): SettingsViewModel = SettingsViewModel(
        observeAvailableMapStyles = ObserveAvailableMapStylesUseCase(repository),
        observeSelectedMapStyle = ObserveSelectedMapStyleUseCase(repository),
        setSelectedMapStyle = SetSelectedMapStyleUseCase(repository),
    )

    private suspend fun SettingsViewModel.awaitContent(
        predicate: (SettingsUiState.Content) -> Boolean = { true },
    ): SettingsUiState.Content = uiState
        .mapNotNull { it as? SettingsUiState.Content }
        .first(predicate)

    private class FakeAppSettingsRepository(
        selectedStyle: MapStyle = MapStyle.defaultStyle,
        private val saveResult: Output<Unit, AppSettingsError> = Output.Success(Unit),
    ) : AppSettingsRepository {

        private val selectedStyleFlow = MutableStateFlow(Output.Success(selectedStyle))

        val savedStyleIds = mutableListOf<String>()

        override fun observeAvailableMapStyles(): Flow<Output<List<MapStyle>, AppSettingsError>> =
            MutableStateFlow(Output.Success(MapStyle.availableStyles))

        override fun observeSelectedMapStyle(): Flow<Output<MapStyle, AppSettingsError>> =
            selectedStyleFlow

        override suspend fun setSelectedMapStyle(styleId: String): Output<Unit, AppSettingsError> {
            savedStyleIds += styleId
            if (saveResult is Output.Success) {
                selectedStyleFlow.value = Output.Success(MapStyle.styleById(styleId) ?: MapStyle.defaultStyle)
            }
            return saveResult
        }
    }
}
