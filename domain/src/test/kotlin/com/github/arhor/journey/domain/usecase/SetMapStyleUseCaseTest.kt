package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.State
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.repository.MapStylesRepository
import com.github.arhor.journey.domain.repository.SettingsRepository
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SetMapStyleUseCaseTest {

    private val mapStylesRepository = mockk<MapStylesRepository>()
    private val settingsRepository = mockk<SettingsRepository>()

    private val subject = SetMapStyleUseCase(
        mapStylesRepository = mapStylesRepository,
        settingsRepository = settingsRepository,
    )

    @Test
    fun `invoke should persist the provided style when it exists`() = runTest {
        // Given
        val selectedStyle = MapStyle.remote(
            id = "voyager",
            name = "Voyager",
            value = "https://example.com/voyager.json",
        )
        every { mapStylesRepository.observeMapStyles() } returns MutableStateFlow(
            State.Content(listOf(selectedStyle)),
        )
        coJustRun { settingsRepository.setSelectedMapStyleId(any()) }

        // When
        subject(selectedStyle.id)

        // Then
        coVerify(exactly = 1) { settingsRepository.setSelectedMapStyleId(selectedStyle.id) }
    }

    @Test
    fun `invoke should persist the first available style when the provided style does not exist`() = runTest {
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
        every { mapStylesRepository.observeMapStyles() } returns MutableStateFlow(
            State.Content(listOf(firstStyle, secondStyle)),
        )
        coJustRun { settingsRepository.setSelectedMapStyleId(any()) }

        // When
        subject("missing-style")

        // Then
        coVerify(exactly = 1) { settingsRepository.setSelectedMapStyleId(firstStyle.id) }
    }

    @Test
    fun `invoke should skip persistence when no styles are available`() = runTest {
        // Given
        every { mapStylesRepository.observeMapStyles() } returns MutableStateFlow(
            State.Content(emptyList()),
        )
        coJustRun { settingsRepository.setSelectedMapStyleId(any()) }

        // When
        subject("missing-style")

        // Then
        coVerify(exactly = 0) { settingsRepository.setSelectedMapStyleId(any()) }
    }

    @Test
    fun `invoke should skip persistence when map styles are still loading`() = runTest {
        // Given
        every { mapStylesRepository.observeMapStyles() } returns MutableStateFlow(State.Loading)
        coJustRun { settingsRepository.setSelectedMapStyleId(any()) }

        // When
        subject("voyager")

        // Then
        coVerify(exactly = 0) { settingsRepository.setSelectedMapStyleId(any()) }
    }
}
