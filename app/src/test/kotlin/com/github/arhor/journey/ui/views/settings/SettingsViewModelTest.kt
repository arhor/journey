package com.github.arhor.journey.ui.views.settings

import com.github.arhor.journey.domain.model.ActivityLogEntry
import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.DistanceUnit
import com.github.arhor.journey.domain.model.HealthConnectAvailability
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.repository.HealthConnectAvailabilityRepository
import com.github.arhor.journey.domain.repository.HealthPermissionRepository
import com.github.arhor.journey.domain.repository.HealthSyncCheckpointRepository
import com.github.arhor.journey.domain.usecase.ObserveActivityLogUseCase
import com.github.arhor.journey.domain.usecase.ObserveAvailableMapStylesUseCase
import com.github.arhor.journey.domain.usecase.ObserveSelectedMapStyleUseCase
import com.github.arhor.journey.domain.usecase.ObserveSettingsUseCase
import com.github.arhor.journey.domain.usecase.SetDistanceUnitUseCase
import com.github.arhor.journey.domain.usecase.SetMapStyleUseCase
import com.github.arhor.journey.domain.usecase.SyncHealthDataUseCase
import com.github.arhor.journey.test.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initialize should expose available and selected map styles when flows emit`() = runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val fixture = createFixture(
            settings = AppSettings(distanceUnit = DistanceUnit.IMPERIAL),
            availableMapStyles = listOf(MapStyle("default", "Default"), MapStyle("dark", "Dark")),
            selectedMapStyle = MapStyle("dark", "Dark"),
        )

        backgroundScope.launch { fixture.vm.uiState.collect() }

        // When
        advanceUntilIdle()

        // Then
        val state = fixture.vm.uiState.first { it is SettingsUiState.Content } as SettingsUiState.Content
        state.distanceUnit shouldBe DistanceUnit.IMPERIAL
        state.selectedMapStyleId shouldBe "dark"
        state.availableMapStyles.map { it.id } shouldBe listOf("default", "dark")
    }

    @Test
    fun `select map style should invoke SetMapStyleUseCase when selection changes`() = runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val fixture = createFixture()
        backgroundScope.launch { fixture.vm.uiState.collect() }
        coEvery { fixture.setMapStyleUseCase.invoke("terrain") } returns Unit
        advanceUntilIdle()

        // When
        fixture.vm.dispatch(SettingsIntent.SelectMapStyle("terrain"))
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { fixture.setMapStyleUseCase.invoke("terrain") }
    }

    private fun createFixture(
        settings: AppSettings = AppSettings(),
        availableMapStyles: List<MapStyle> = listOf(MapStyle("default", "Default"), MapStyle("terrain", "Terrain")),
        selectedMapStyle: MapStyle = MapStyle("default", "Default"),
    ): Fixture {
        val observeSettings = mockk<ObserveSettingsUseCase>()
        val observeAvailableMapStyles = mockk<ObserveAvailableMapStylesUseCase>()
        val observeSelectedMapStyle = mockk<ObserveSelectedMapStyleUseCase>()
        val observeActivityLog = mockk<ObserveActivityLogUseCase>()
        val setDistanceUnitUseCase = mockk<SetDistanceUnitUseCase>(relaxed = true)
        val setMapStyleUseCase = mockk<SetMapStyleUseCase>()
        val healthPermissionRepository = mockk<HealthPermissionRepository>()
        val availabilityRepository = mockk<HealthConnectAvailabilityRepository>()
        val checkpointRepository = mockk<HealthSyncCheckpointRepository>()
        val syncHealthData = mockk<SyncHealthDataUseCase>()

        every { observeSettings.invoke() } returns flowOf(settings)
        every { observeAvailableMapStyles.invoke() } returns flowOf(availableMapStyles)
        every { observeSelectedMapStyle.invoke() } returns flowOf(selectedMapStyle)
        every { observeActivityLog.invoke() } returns flowOf(emptyList<ActivityLogEntry>())
        every { availabilityRepository.checkAvailability() } returns HealthConnectAvailability.AVAILABLE
        coEvery { healthPermissionRepository.getMissingPermissions() } returns emptySet()
        coEvery { checkpointRepository.getLastSuccessfulSyncAt() } returns Instant.parse("2026-01-01T00:00:00Z")

        val vm = SettingsViewModel(
            observeSettings = observeSettings,
            observeAvailableMapStyles = observeAvailableMapStyles,
            observeSelectedMapStyle = observeSelectedMapStyle,
            observeActivityLog = observeActivityLog,
            setDistanceUnit = setDistanceUnitUseCase,
            setMapStyle = setMapStyleUseCase,
            healthPermissionRepository = healthPermissionRepository,
            healthConnectAvailabilityRepository = availabilityRepository,
            healthSyncCheckpointRepository = checkpointRepository,
            syncHealthData = syncHealthData,
            clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
        )

        return Fixture(vm, setMapStyleUseCase)
    }

    private data class Fixture(
        val vm: SettingsViewModel,
        val setMapStyleUseCase: SetMapStyleUseCase,
    )
}
