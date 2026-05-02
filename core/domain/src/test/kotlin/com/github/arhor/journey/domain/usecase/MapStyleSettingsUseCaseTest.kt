package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.error.AppSettingsError
import com.github.arhor.journey.domain.repository.AppSettingsRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MapStyleSettingsUseCaseTest {

    @Test
    fun `ObserveAvailableMapStylesUseCase should delegate to repository`() = runTest {
        // Given
        val repository = FakeAppSettingsRepository()
        val useCase = ObserveAvailableMapStylesUseCase(repository)

        // When
        val actual = useCase().first()

        // Then
        actual shouldBe Output.Success(MapStyle.availableStyles)
    }

    @Test
    fun `ObserveSelectedMapStyleUseCase should delegate to repository`() = runTest {
        // Given
        val repository = FakeAppSettingsRepository(
            selectedStyle = MapStyle.styleById("urban-noir")!!,
        )
        val useCase = ObserveSelectedMapStyleUseCase(repository)

        // When
        val actual = useCase().first()

        // Then
        actual shouldBe Output.Success(MapStyle.styleById("urban-noir")!!)
    }

    @Test
    fun `SetSelectedMapStyleUseCase should delegate selected style id to repository`() = runTest {
        // Given
        val repository = FakeAppSettingsRepository()
        val useCase = SetSelectedMapStyleUseCase(repository)

        // When
        val actual = useCase("light")

        // Then
        actual shouldBe Output.Success(Unit)
        repository.savedStyleIds shouldBe listOf("light")
    }

    private class FakeAppSettingsRepository(
        selectedStyle: MapStyle = MapStyle.defaultStyle,
    ) : AppSettingsRepository {

        private val selectedStyleFlow = MutableStateFlow(Output.Success(selectedStyle))

        val savedStyleIds = mutableListOf<String>()

        override fun observeAvailableMapStyles(): Flow<Output<List<MapStyle>, AppSettingsError>> =
            MutableStateFlow(Output.Success(MapStyle.availableStyles))

        override fun observeSelectedMapStyle(): Flow<Output<MapStyle, AppSettingsError>> =
            selectedStyleFlow

        override suspend fun setSelectedMapStyle(styleId: String): Output<Unit, AppSettingsError> {
            savedStyleIds += styleId
            selectedStyleFlow.value = Output.Success(MapStyle.styleById(styleId) ?: MapStyle.defaultStyle)
            return Output.Success(Unit)
        }
    }
}
