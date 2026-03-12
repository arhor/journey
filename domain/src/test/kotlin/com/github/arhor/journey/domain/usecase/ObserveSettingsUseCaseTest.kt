package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.DistanceUnit
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.repository.MapStylesRepository
import com.github.arhor.journey.domain.repository.SettingsRepository
import io.kotest.matchers.shouldBe
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObserveSettingsUseCaseTest {

    private val settingsRepository = mockk<SettingsRepository>()
    private val mapStylesRepository = mockk<MapStylesRepository>()

    private val subject = ObserveSettingsUseCase(
        settingsRepository = settingsRepository,
        mapStylesRepository = mapStylesRepository,
    )

    @Test
    fun `invoke should set first available style when selected style is not set`() = runTest {
        // Given
        val firstStyle = MapStyle.remote(
            id = "voyager",
            name = "Voyager",
            value = "https://example.com/voyager.json",
        )
        val secondStyle = MapStyle.remote(
            id = "positron",
            name = "Positron",
            value = "https://example.com/positron.json",
        )
        every { settingsRepository.observeSettings() } returns flowOf(
            AppSettings(
                distanceUnit = DistanceUnit.METRIC,
                selectedMapStyleId = null,
            ),
        )
        every { mapStylesRepository.findAll() } returns listOf(firstStyle, secondStyle)
        coJustRun { settingsRepository.setSelectedMapStyleId(any()) }

        // When
        val result = subject().first()

        // Then
        result.selectedMapStyleId shouldBe firstStyle.id
        coVerify(exactly = 1) { settingsRepository.setSelectedMapStyleId(firstStyle.id) }
    }

    @Test
    fun `invoke should set first available style when selected style is invalid`() = runTest {
        // Given
        val firstStyle = MapStyle.remote(
            id = "voyager",
            name = "Voyager",
            value = "https://example.com/voyager.json",
        )
        every { settingsRepository.observeSettings() } returns flowOf(
            AppSettings(
                distanceUnit = DistanceUnit.METRIC,
                selectedMapStyleId = "unknown-style",
            ),
        )
        every { mapStylesRepository.findAll() } returns listOf(firstStyle)
        coJustRun { settingsRepository.setSelectedMapStyleId(any()) }

        // When
        val result = subject().first()

        // Then
        result.selectedMapStyleId shouldBe firstStyle.id
        coVerify(exactly = 1) { settingsRepository.setSelectedMapStyleId(firstStyle.id) }
    }

    @Test
    fun `invoke should keep selected style when it is available`() = runTest {
        // Given
        val selectedStyle = MapStyle.remote(
            id = "voyager",
            name = "Voyager",
            value = "https://example.com/voyager.json",
        )
        val anotherStyle = MapStyle.remote(
            id = "positron",
            name = "Positron",
            value = "https://example.com/positron.json",
        )
        every { settingsRepository.observeSettings() } returns flowOf(
            AppSettings(
                distanceUnit = DistanceUnit.IMPERIAL,
                selectedMapStyleId = selectedStyle.id,
            ),
        )
        every { mapStylesRepository.findAll() } returns listOf(selectedStyle, anotherStyle)
        coJustRun { settingsRepository.setSelectedMapStyleId(any()) }

        // When
        val result = subject().first()

        // Then
        result.selectedMapStyleId shouldBe selectedStyle.id
        coVerify(exactly = 0) { settingsRepository.setSelectedMapStyleId(any()) }
    }

    @Test
    fun `invoke should keep selected style null when no styles are available`() = runTest {
        // Given
        every { settingsRepository.observeSettings() } returns flowOf(
            AppSettings(
                distanceUnit = DistanceUnit.METRIC,
                selectedMapStyleId = null,
            ),
        )
        every { mapStylesRepository.findAll() } returns emptyList()
        coJustRun { settingsRepository.setSelectedMapStyleId(any()) }

        // When
        val result = subject().first()

        // Then
        result.selectedMapStyleId shouldBe null
        coVerify(exactly = 0) { settingsRepository.setSelectedMapStyleId(any()) }
    }
}
