package com.github.arhor.journey.ui.views.settings

import com.github.arhor.journey.data.healthconnect.HealthConnectPermissionGateway
import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.DistanceUnit
import com.github.arhor.journey.domain.model.HealthConnectAvailability
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.HealthDataSyncPayload
import com.github.arhor.journey.domain.model.HealthDataSyncSummary
import com.github.arhor.journey.domain.model.Resource
import com.github.arhor.journey.domain.repository.HealthConnectAvailabilityRepository
import com.github.arhor.journey.domain.repository.HealthSyncCheckpointRepository
import com.github.arhor.journey.domain.usecase.ObserveActivityLogUseCase
import com.github.arhor.journey.domain.usecase.ObserveSettingsUseCase
import com.github.arhor.journey.domain.usecase.SetDistanceUnitUseCase
import com.github.arhor.journey.domain.usecase.SetMapStyleUseCase
import com.github.arhor.journey.domain.usecase.SyncHealthDataUseCase
import com.github.arhor.journey.domain.usecase.SyncHealthDataUseCaseResult
import com.github.arhor.journey.test.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initialize should expose granted health connect state when required permissions are already granted`() =
        runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val fixture = createFixture(
            availability = HealthConnectAvailability.AVAILABLE,
            missingPermissions = emptySet(),
            lastSuccessfulSyncAt = Instant.parse("2026-01-02T00:00:00Z"),
            settings = AppSettings(distanceUnit = DistanceUnit.IMPERIAL, mapStyle = MapStyle.DARK),
        )
        backgroundScope.launch { fixture.vm.uiState.collect() }

        // When
        advanceUntilIdle()

        // Then
        val state = fixture.vm.uiState.first { it is SettingsUiState.Content } as SettingsUiState.Content
        state.distanceUnit shouldBe DistanceUnit.IMPERIAL
        state.mapStyle shouldBe MapStyle.DARK
        state.healthConnectAvailability shouldBe HealthConnectAvailability.AVAILABLE
        state.healthConnectConnectionStatus shouldBe HealthConnectConnectionStatus.CONNECTED
        state.healthConnectPermissionStatus shouldBe HealthConnectPermissionStatus.GRANTED
        state.lastSyncTimestamp shouldBe Instant.parse("2026-01-02T00:00:00Z")
    }

    @Test
    fun `select distance unit should invoke SetDistanceUnitUseCase when selection changes`() =
        runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val fixture = createFixture()
        coEvery { fixture.setDistanceUnitUseCase.invoke(DistanceUnit.IMPERIAL) } returns Unit
        backgroundScope.launch { fixture.vm.uiState.collect() }
        advanceUntilIdle()

        // When
        fixture.vm.dispatch(SettingsIntent.SelectDistanceUnit(DistanceUnit.IMPERIAL))
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { fixture.setDistanceUnitUseCase.invoke(DistanceUnit.IMPERIAL) }
        (fixture.vm.uiState.value as SettingsUiState.Content).isUpdating shouldBe false
    }

    @Test
    fun `select map style should invoke SetMapStyleUseCase when selection changes`() =
        runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val fixture = createFixture()
        coEvery { fixture.setMapStyleUseCase.invoke(MapStyle.TERRAIN) } returns Unit
        backgroundScope.launch { fixture.vm.uiState.collect() }
        advanceUntilIdle()

        // When
        fixture.vm.dispatch(SettingsIntent.SelectMapStyle(MapStyle.TERRAIN))
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { fixture.setMapStyleUseCase.invoke(MapStyle.TERRAIN) }
        (fixture.vm.uiState.value as SettingsUiState.Content).isUpdating shouldBe false
    }

    @Test
    fun `connect health connect should emit permission request effect when permissions are missing`() =
        runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val fixture = createFixture(missingPermissions = setOf(permissionExercise))
        backgroundScope.launch { fixture.vm.uiState.collect() }
        advanceUntilIdle()
        val effect = async { fixture.vm.effects.first() }

        // When
        fixture.vm.dispatch(SettingsIntent.ConnectHealthConnect)
        advanceUntilIdle()

        // Then
        effect.await() shouldBe SettingsEffect.LaunchHealthConnectPermissionRequest(setOf(permissionExercise))
        val state = fixture.vm.uiState.value as SettingsUiState.Content
        state.healthConnectPermissionStatus shouldBe HealthConnectPermissionStatus.REQUESTING
        state.missingHealthConnectPermissions shouldBe setOf(permissionExercise)
    }

    @Test
    fun `connect health connect should open management flow when health connect needs update or install`() =
        runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val fixture = createFixture(availability = HealthConnectAvailability.NEEDS_UPDATE_OR_INSTALL)
        backgroundScope.launch { fixture.vm.uiState.collect() }
        advanceUntilIdle()
        val collectedEffects = mutableListOf<SettingsEffect>()
        backgroundScope.launch { fixture.vm.effects.collect { collectedEffects += it } }

        // When
        fixture.vm.dispatch(SettingsIntent.ConnectHealthConnect)
        advanceUntilIdle()

        // Then
        collectedEffects shouldBe listOf(
            SettingsEffect.OpenHealthConnectManagement,
            SettingsEffect.Error("Install or update Health Connect to continue."),
        )
        val state = fixture.vm.uiState.value as SettingsUiState.Content
        state.healthConnectAvailability shouldBe HealthConnectAvailability.NEEDS_UPDATE_OR_INSTALL
        state.healthConnectConnectionStatus shouldBe HealthConnectConnectionStatus.DISCONNECTED
    }

    @Test
    fun `manage health connect permissions should emit management effect when health connect is supported`() =
        runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val fixture = createFixture()
        backgroundScope.launch { fixture.vm.uiState.collect() }
        advanceUntilIdle()
        val effect = async { fixture.vm.effects.first() }

        // When
        fixture.vm.dispatch(SettingsIntent.ManageHealthConnectPermissions)
        advanceUntilIdle()

        // Then
        effect.await() shouldBe SettingsEffect.OpenHealthConnectManagement
    }

    @Test
    fun `manual sync health data should invoke sync use case and persist checkpoint when permissions are granted`() =
        runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val syncPayload = HealthDataSyncPayload(
            summary = HealthDataSyncSummary(
                importedEntriesCount = 1,
                importedStepsCount = 0,
                importedSessionsCount = 1,
                importedSessionDuration = Duration.ofMinutes(30),
            ),
            importedEntries = emptyList(),
        )
        val fixture = createFixture(
            missingPermissions = emptySet(),
            syncResult = SyncHealthDataUseCaseResult.Success(syncPayload),
        )
        backgroundScope.launch { fixture.vm.uiState.collect() }
        advanceUntilIdle()
        val effect = async { fixture.vm.effects.first() }

        // When
        fixture.vm.dispatch(SettingsIntent.ManualSyncHealthData)
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) {
            fixture.syncHealthData.invoke(
                timeRange = match {
                    it.startTime == Instant.parse("2025-12-29T00:00:00Z") &&
                        it.endTime == Instant.parse("2026-01-01T00:00:00Z")
                },
                selectedDataTypes = setOf(com.github.arhor.journey.domain.model.HealthDataType.SESSIONS),
                syncMode = com.github.arhor.journey.domain.model.HealthDataSyncMode.MANUAL,
            )
        }
        coVerify(exactly = 1) {
            fixture.healthSyncCheckpointRepository.setLastSuccessfulSyncAt(Instant.parse("2026-01-01T00:00:00Z"))
        }
        effect.await() shouldBe SettingsEffect.Success("Health data sync completed.")
        val state = fixture.vm.uiState.value as SettingsUiState.Content
        state.healthConnectConnectionStatus shouldBe HealthConnectConnectionStatus.CONNECTED
        state.healthConnectPermissionStatus shouldBe HealthConnectPermissionStatus.GRANTED
        state.lastSyncTimestamp shouldBe Instant.parse("2026-01-01T00:00:00Z")
    }

    @Test
    fun `handle health connect permission result should re-read permissions and continue pending sync when sync was waiting for approval`() =
        runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val settingsFlow = MutableSharedFlow<Resource<AppSettings>>(replay = 1).apply {
            tryEmit(Resource.Success(AppSettings(distanceUnit = DistanceUnit.METRIC)))
        }
        val observeSettingsUseCase = mockk<ObserveSettingsUseCase>()
        val observeActivityLogUseCase = mockk<ObserveActivityLogUseCase>()
        val setDistanceUnitUseCase = mockk<SetDistanceUnitUseCase>()
        val setMapStyleUseCase = mockk<SetMapStyleUseCase>()
        val permissionGateway = mockk<HealthConnectPermissionGateway>()
        val availabilityRepository = mockk<HealthConnectAvailabilityRepository>()
        val checkpointRepository = mockk<HealthSyncCheckpointRepository>()
        val syncHealthData = mockk<SyncHealthDataUseCase>()
        every { observeSettingsUseCase.invoke() } returns settingsFlow
        every { observeActivityLogUseCase.invoke() } returns flowOf(emptyList())
        every { availabilityRepository.checkAvailability() } returns HealthConnectAvailability.AVAILABLE
        every { permissionGateway.requiredPermissions } returns setOf(permissionExercise)
        coEvery { permissionGateway.getMissingPermissions() } returnsMany listOf(
            setOf(permissionExercise),
            setOf(permissionExercise),
            emptySet(),
        )
        coEvery { checkpointRepository.getLastSuccessfulSyncAt() } returns null
        coEvery { checkpointRepository.setLastSuccessfulSyncAt(any()) } returns Unit
        coEvery { syncHealthData.invoke(any(), any(), any()) } returns SyncHealthDataUseCaseResult.Success(
            HealthDataSyncPayload(
                summary = HealthDataSyncSummary(
                    importedEntriesCount = 1,
                    importedStepsCount = 0,
                    importedSessionsCount = 1,
                    importedSessionDuration = Duration.ofMinutes(15),
                ),
                importedEntries = emptyList(),
            ),
        )

        val vm = SettingsViewModel(
            observeSettings = observeSettingsUseCase,
            observeActivityLog = observeActivityLogUseCase,
            setDistanceUnit = setDistanceUnitUseCase,
            setMapStyle = setMapStyleUseCase,
            healthConnectPermissionGateway = permissionGateway,
            healthConnectAvailabilityRepository = availabilityRepository,
            healthSyncCheckpointRepository = checkpointRepository,
            syncHealthData = syncHealthData,
            clock = fixedClock,
        )
        backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()
        val collectedEffects = mutableListOf<SettingsEffect>()
        backgroundScope.launch { vm.effects.collect { collectedEffects += it } }

        // When
        vm.dispatch(SettingsIntent.ManualSyncHealthData)
        advanceUntilIdle()
        vm.dispatch(SettingsIntent.HandleHealthConnectPermissionResult(emptySet()))
        advanceUntilIdle()

        // Then
        collectedEffects shouldBe listOf(
            SettingsEffect.LaunchHealthConnectPermissionRequest(setOf(permissionExercise)),
            SettingsEffect.Success("Health data sync completed."),
        )
        coVerify(exactly = 1) {
            syncHealthData.invoke(any(), setOf(com.github.arhor.journey.domain.model.HealthDataType.SESSIONS), com.github.arhor.journey.domain.model.HealthDataSyncMode.MANUAL)
        }
        coVerify(exactly = 1) { checkpointRepository.setLastSuccessfulSyncAt(Instant.parse("2026-01-01T00:00:00Z")) }
        val state = vm.uiState.value as SettingsUiState.Content
        state.healthConnectPermissionStatus shouldBe HealthConnectPermissionStatus.GRANTED
        state.lastSyncTimestamp shouldBe Instant.parse("2026-01-01T00:00:00Z")
    }

    @Test
    fun `handle health connect permission result should show recovery message when permissions remain missing twice`() =
        runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val settingsFlow = MutableSharedFlow<Resource<AppSettings>>(replay = 1).apply {
            tryEmit(Resource.Success(AppSettings(distanceUnit = DistanceUnit.METRIC)))
        }
        val observeSettingsUseCase = mockk<ObserveSettingsUseCase>()
        val observeActivityLogUseCase = mockk<ObserveActivityLogUseCase>()
        val setDistanceUnitUseCase = mockk<SetDistanceUnitUseCase>()
        val setMapStyleUseCase = mockk<SetMapStyleUseCase>()
        val permissionGateway = mockk<HealthConnectPermissionGateway>()
        val availabilityRepository = mockk<HealthConnectAvailabilityRepository>()
        val checkpointRepository = mockk<HealthSyncCheckpointRepository>()
        val syncHealthData = mockk<SyncHealthDataUseCase>()
        every { observeSettingsUseCase.invoke() } returns settingsFlow
        every { observeActivityLogUseCase.invoke() } returns flowOf(emptyList())
        every { availabilityRepository.checkAvailability() } returns HealthConnectAvailability.AVAILABLE
        every { permissionGateway.requiredPermissions } returns setOf(permissionExercise)
        coEvery { permissionGateway.getMissingPermissions() } returnsMany listOf(
            setOf(permissionExercise),
            setOf(permissionExercise),
            setOf(permissionExercise),
            setOf(permissionExercise),
            setOf(permissionExercise),
        )
        coEvery { checkpointRepository.getLastSuccessfulSyncAt() } returns null

        val vm = SettingsViewModel(
            observeSettings = observeSettingsUseCase,
            observeActivityLog = observeActivityLogUseCase,
            setDistanceUnit = setDistanceUnitUseCase,
            setMapStyle = setMapStyleUseCase,
            healthConnectPermissionGateway = permissionGateway,
            healthConnectAvailabilityRepository = availabilityRepository,
            healthSyncCheckpointRepository = checkpointRepository,
            syncHealthData = syncHealthData,
            clock = fixedClock,
        )
        backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()
        val collectedEffects = mutableListOf<SettingsEffect>()
        backgroundScope.launch { vm.effects.collect { collectedEffects += it } }

        // When
        vm.dispatch(SettingsIntent.ConnectHealthConnect)
        advanceUntilIdle()
        vm.dispatch(SettingsIntent.HandleHealthConnectPermissionResult(emptySet()))
        advanceUntilIdle()
        vm.dispatch(SettingsIntent.ConnectHealthConnect)
        advanceUntilIdle()
        vm.dispatch(SettingsIntent.HandleHealthConnectPermissionResult(emptySet()))
        advanceUntilIdle()

        // Then
        collectedEffects.last() shouldBe SettingsEffect.Error(
            "Health Connect permissions are still missing. Use Manage Permissions to continue.",
        )
        val state = vm.uiState.value as SettingsUiState.Content
        state.healthConnectPermissionStatus shouldBe HealthConnectPermissionStatus.DENIED
        state.missingHealthConnectPermissions shouldBe setOf(permissionExercise)
    }

    @Test
    fun `initialize should expose empty imported summaries when activity log is empty`() =
        runTest(mainDispatcherRule.testDispatcher) {
        // Given
        val fixture = createFixture(activityLog = emptyList())
        backgroundScope.launch { fixture.vm.uiState.collect() }

        // When
        advanceUntilIdle()

        // Then
        val state = fixture.vm.uiState.first { it is SettingsUiState.Content } as SettingsUiState.Content
        state.importedTodaySummary.importedActivities shouldBe 0
        state.importedTodaySummary.importedSteps shouldBe 0L
        state.importedWeekSummary.importedActivities shouldBe 0
        state.importedWeekSummary.importedSteps shouldBe 0L
    }

    private fun createFixture(
        availability: HealthConnectAvailability = HealthConnectAvailability.AVAILABLE,
        missingPermissions: Set<String> = emptySet(),
        lastSuccessfulSyncAt: Instant? = null,
        activityLog: List<com.github.arhor.journey.domain.model.ActivityLogEntry> = emptyList(),
        settings: AppSettings = AppSettings(distanceUnit = DistanceUnit.METRIC, mapStyle = MapStyle.DEFAULT),
        syncResult: SyncHealthDataUseCaseResult = SyncHealthDataUseCaseResult.Success(
            HealthDataSyncPayload(
                summary = HealthDataSyncSummary(
                    importedEntriesCount = 0,
                    importedStepsCount = 0,
                    importedSessionsCount = 0,
                    importedSessionDuration = Duration.ZERO,
                ),
                importedEntries = emptyList(),
            ),
        ),
    ): Fixture {
        val settingsFlow = MutableSharedFlow<Resource<AppSettings>>(replay = 1).apply {
            tryEmit(Resource.Success(settings))
        }
        val observeSettingsUseCase = mockk<ObserveSettingsUseCase>()
        val observeActivityLogUseCase = mockk<ObserveActivityLogUseCase>()
        val setDistanceUnitUseCase = mockk<SetDistanceUnitUseCase>()
        val setMapStyleUseCase = mockk<SetMapStyleUseCase>()
        val permissionGateway = mockk<HealthConnectPermissionGateway>()
        val availabilityRepository = mockk<HealthConnectAvailabilityRepository>()
        val checkpointRepository = mockk<HealthSyncCheckpointRepository>()
        val syncHealthData = mockk<SyncHealthDataUseCase>()
        every { observeSettingsUseCase.invoke() } returns settingsFlow
        every { observeActivityLogUseCase.invoke() } returns flowOf(activityLog)
        every { availabilityRepository.checkAvailability() } returns availability
        every { permissionGateway.requiredPermissions } returns setOf(permissionExercise)
        coEvery { permissionGateway.getMissingPermissions() } returns missingPermissions
        coEvery { checkpointRepository.getLastSuccessfulSyncAt() } returns lastSuccessfulSyncAt
        coEvery { checkpointRepository.setLastSuccessfulSyncAt(any()) } returns Unit
        coEvery { syncHealthData.invoke(any(), any(), any()) } returns syncResult

        val vm = SettingsViewModel(
            observeSettings = observeSettingsUseCase,
            observeActivityLog = observeActivityLogUseCase,
            setDistanceUnit = setDistanceUnitUseCase,
            setMapStyle = setMapStyleUseCase,
            healthConnectPermissionGateway = permissionGateway,
            healthConnectAvailabilityRepository = availabilityRepository,
            healthSyncCheckpointRepository = checkpointRepository,
            syncHealthData = syncHealthData,
            clock = fixedClock,
        )

        return Fixture(
            vm = vm,
            setDistanceUnitUseCase = setDistanceUnitUseCase,
            setMapStyleUseCase = setMapStyleUseCase,
            syncHealthData = syncHealthData,
            healthSyncCheckpointRepository = checkpointRepository,
        )
    }

    private data class Fixture(
        val vm: SettingsViewModel,
        val setDistanceUnitUseCase: SetDistanceUnitUseCase,
        val setMapStyleUseCase: SetMapStyleUseCase,
        val syncHealthData: SyncHealthDataUseCase,
        val healthSyncCheckpointRepository: HealthSyncCheckpointRepository,
    )

    private companion object {
        const val permissionExercise = "android.permission.health.READ_EXERCISE"
        val fixedClock: Clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
    }
}
