package com.github.arhor.journey.feature.settings

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.error.AppSettingsError
import com.github.arhor.journey.domain.model.error.MapStylesError
import com.github.arhor.journey.domain.repository.MapStylesRepository
import com.github.arhor.journey.domain.repository.SettingsRepository
import com.github.arhor.journey.domain.usecase.ObserveMapStylesUseCase
import com.github.arhor.journey.domain.usecase.ObserveSettingsUseCase
import com.github.arhor.journey.domain.usecase.SetMapStyleUseCase
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test

class SettingsViewModelTest {

    @Test
    fun `buildUiState should expose content when settings and map styles are loaded successfully`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        lateinit var viewModel: SettingsViewModel
        try {
            val expectedMapStyles = listOf(
                MapStyle.remote(id = "remote", name = "Remote", value = "https://example.com/style.json"),
                MapStyle.bundle(id = "local", name = "Local", value = "asset://map/styles/local.json"),
            )
            val settingsRepository = FakeSettingsRepository(
                initialOutput = Output.Success(
                    AppSettings(
                        selectedMapStyleId = "remote",
                    ),
                ),
            )
            val mapStylesRepository = FakeMapStylesRepository(
                initialOutput = Output.Success(expectedMapStyles),
            )
            viewModel = createViewModel(settingsRepository, mapStylesRepository)

            // When
            val actualDeferred = async { viewModel.uiState.first { it !is SettingsUiState.Loading } }
            advanceUntilIdle()
            val actual = actualDeferred.await()

            // Then
            actual shouldBe SettingsUiState.Content(
                isUpdating = false,
                selectedMapStyleId = "remote",
                availableMapStyles = expectedMapStyles,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `buildUiState should expose failure when settings output contains error message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        lateinit var viewModel: SettingsViewModel
        try {
            val settingsRepository = FakeSettingsRepository(
                initialOutput = Output.Failure(
                    AppSettingsError.LoadingFailed(message = "Settings are unavailable."),
                ),
            )
            val mapStylesRepository = FakeMapStylesRepository(initialOutput = Output.Success(emptyList()))
            viewModel = createViewModel(settingsRepository, mapStylesRepository)

            // When
            val actualDeferred = async { viewModel.uiState.first { it !is SettingsUiState.Loading } }
            advanceUntilIdle()
            val actual = actualDeferred.await()

            // Then
            actual shouldBe SettingsUiState.Failure(errorMessage = "Settings are unavailable.")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `buildUiState should expose failure with cause message when settings error message is missing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        lateinit var viewModel: SettingsViewModel
        try {
            val settingsRepository = FakeSettingsRepository(
                initialOutput = Output.Failure(
                    AppSettingsError.LoadingFailed(
                        cause = IllegalStateException("Settings loading failed in storage."),
                        message = null,
                    ),
                ),
            )
            val mapStylesRepository = FakeMapStylesRepository(initialOutput = Output.Success(emptyList()))
            viewModel = createViewModel(settingsRepository, mapStylesRepository)

            // When
            val actualDeferred = async { viewModel.uiState.first { it !is SettingsUiState.Loading } }
            advanceUntilIdle()
            val actual = actualDeferred.await()

            // Then
            actual shouldBe SettingsUiState.Failure(errorMessage = "Settings loading failed in storage.")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `buildUiState should expose failure when settings stream throws exception`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        lateinit var viewModel: SettingsViewModel
        try {
            val settingsRepository = ThrowingSettingsRepository(
                throwable = IllegalStateException("Settings stream crashed."),
            )
            val mapStylesRepository = FakeMapStylesRepository(initialOutput = Output.Success(emptyList()))
            viewModel = createViewModel(settingsRepository, mapStylesRepository)

            // When
            val actualDeferred = async { viewModel.uiState.first { it !is SettingsUiState.Loading } }
            advanceUntilIdle()
            val actual = actualDeferred.await()

            // Then
            actual shouldBe SettingsUiState.Failure(errorMessage = "Settings stream crashed.")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `buildUiState should expose fallback failure when settings stream throws exception without message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        lateinit var viewModel: SettingsViewModel
        try {
            val settingsRepository = ThrowingSettingsRepository(throwable = RuntimeException())
            val mapStylesRepository = FakeMapStylesRepository(initialOutput = Output.Success(emptyList()))
            viewModel = createViewModel(settingsRepository, mapStylesRepository)

            // When
            val actualDeferred = async { viewModel.uiState.first { it !is SettingsUiState.Loading } }
            advanceUntilIdle()
            val actual = actualDeferred.await()

            // Then
            actual shouldBe SettingsUiState.Failure(errorMessage = "Can't load settings.")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `dispatch should persist selected map style when style id exists`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        lateinit var viewModel: SettingsViewModel
        try {
            val settingsRepository = FakeSettingsRepository()
            val mapStylesRepository = FakeMapStylesRepository(
                initialOutput = Output.Success(
                    listOf(
                        MapStyle.remote(id = "remote", name = "Remote", value = "https://example.com/style.json"),
                    ),
                ),
            )
            viewModel = createViewModel(settingsRepository, mapStylesRepository)
            runCurrent()

            // When
            viewModel.dispatch(SettingsIntent.SelectMapStyle(styleId = "remote"))
            advanceUntilIdle()

            // Then
            settingsRepository.savedMapStyleIds shouldBe listOf("remote")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `dispatch should not persist map style when selected style id is unavailable`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        lateinit var viewModel: SettingsViewModel
        try {
            val settingsRepository = FakeSettingsRepository()
            val mapStylesRepository = FakeMapStylesRepository(
                initialOutput = Output.Success(
                    listOf(
                        MapStyle.remote(id = "remote", name = "Remote", value = "https://example.com/style.json"),
                    ),
                ),
            )
            viewModel = createViewModel(settingsRepository, mapStylesRepository)
            runCurrent()

            // When
            viewModel.dispatch(SettingsIntent.SelectMapStyle(styleId = "missing"))
            advanceUntilIdle()

            // Then
            settingsRepository.savedMapStyleIds shouldBe emptyList()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `dispatch should emit fallback error effect when map style update fails without message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        // Given
        lateinit var viewModel: SettingsViewModel
        try {
            val settingsRepository = FakeSettingsRepository().apply {
                setSelectedMapStyleIdError = RuntimeException()
            }
            val mapStylesRepository = FakeMapStylesRepository(
                initialOutput = Output.Success(
                    listOf(
                        MapStyle.remote(id = "remote", name = "Remote", value = "https://example.com/style.json"),
                    ),
                ),
            )
            viewModel = createViewModel(settingsRepository, mapStylesRepository)
            runCurrent()
            val effectDeferred = async { viewModel.effects.first() }
            runCurrent()

            // When
            viewModel.dispatch(SettingsIntent.SelectMapStyle(styleId = "remote"))
            advanceUntilIdle()

            // Then
            effectDeferred.await() shouldBe SettingsEffect.Error(message = "Failed to update map style.")
            settingsRepository.savedMapStyleIds shouldBe emptyList()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(
        settingsRepository: SettingsRepository,
        mapStylesRepository: MapStylesRepository,
    ): SettingsViewModel {
        val observeSettings = ObserveSettingsUseCase(settingsRepository)
        val observeMapStyles = ObserveMapStylesUseCase(mapStylesRepository)
        val setMapStyle = SetMapStyleUseCase(settingsRepository, mapStylesRepository)
        return SettingsViewModel(
            observeSettings = observeSettings,
            observeMapStyles = observeMapStyles,
            setMapStyle = setMapStyle,
        )
    }

    private class FakeSettingsRepository(
        initialOutput: Output<AppSettings, AppSettingsError> = Output.Success(
            AppSettings(
                selectedMapStyleId = null,
            ),
        ),
    ) : SettingsRepository {
        private val settingsOutput = MutableStateFlow(initialOutput)

        val savedMapStyleIds = mutableListOf<String>()

        var setSelectedMapStyleIdError: Throwable? = null

        override fun observeSettings(): Flow<Output<AppSettings, AppSettingsError>> = settingsOutput

        override suspend fun setSelectedMapStyleId(styleId: String) {
            setSelectedMapStyleIdError?.let { throw it }
            savedMapStyleIds += styleId
        }
    }

    private class ThrowingSettingsRepository(
        private val throwable: Throwable,
    ) : SettingsRepository {
        override fun observeSettings(): Flow<Output<AppSettings, AppSettingsError>> =
            flow { throw throwable }

        override suspend fun setSelectedMapStyleId(styleId: String) = Unit
    }

    private class FakeMapStylesRepository(
        initialOutput: Output<List<MapStyle>, MapStylesError>,
    ) : MapStylesRepository {
        private val mapStylesOutput = MutableStateFlow(initialOutput)

        override fun observeMapStyles(): StateFlow<Output<List<MapStyle>, MapStylesError>> = mapStylesOutput
    }
}
