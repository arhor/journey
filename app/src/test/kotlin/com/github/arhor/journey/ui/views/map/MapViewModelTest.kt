package com.github.arhor.journey.ui.views.map

import com.github.arhor.journey.core.common.State
import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.DistanceUnit
import com.github.arhor.journey.domain.model.ExplorationProgress
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.usecase.DiscoverPointOfInterestUseCase
import com.github.arhor.journey.domain.usecase.GetAllMapStylesUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationProgressUseCase
import com.github.arhor.journey.domain.usecase.ObservePointsOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveSettingsUseCase
import com.github.arhor.journey.ui.views.map.model.LatLng
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Test

class MapViewModelTest {

    private val observePointsOfInterest = mockk<ObservePointsOfInterestUseCase>()
    private val observeExplorationProgress = mockk<ObserveExplorationProgressUseCase>()
    private val observeSettings = mockk<ObserveSettingsUseCase>()
    private val getAllMapStyles = mockk<GetAllMapStylesUseCase>()
    private val discoverPointOfInterest = mockk<DiscoverPointOfInterestUseCase>()

    @Test
    fun `dispatch should emit permission request effect when recenter is clicked`() = runTest {
        // Given
        val subject = createSubject()
        val effectDeferred = async {
            withTimeout(5_000) {
                subject.effects.first()
            }
        }

        // When
        runCurrent()
        invokePrivate(subject, "onRecenterClicked")

        // Then
        effectDeferred.await() shouldBe MapEffect.RequestLocationPermission
    }

    @Test
    fun `dispatch should show message when recenter permission is denied`() = runTest {
        // Given
        val subject = createSubject()
        val effectDeferred = async {
            withTimeout(5_000) {
                subject.effects.first()
            }
        }

        // When
        runCurrent()
        invokePrivate(
            subject,
            "onRecenterPermissionResolved",
            MapIntent.RecenterPermissionResolved(
                isGranted = false,
                location = null,
            ),
        )

        // Then
        effectDeferred.await() shouldBe MapEffect.ShowMessage("Location permission denied.")
    }

    @Test
    fun `dispatch should recenter map when recenter permission is granted and location is available`() = runTest {
        // Given
        val subject = createSubject()
        val uiStateBefore = subject.uiState.first { it is MapUiState.Content } as MapUiState.Content
        val userLocation = LatLng(latitude = 48.8566, longitude = 2.3522)

        // When
        subject.dispatch(
            MapIntent.RecenterPermissionResolved(
                isGranted = true,
                location = userLocation,
            ),
        )
        val uiStateAfter = subject.uiState
            .first { it is MapUiState.Content && it.cameraPosition.target == userLocation } as MapUiState.Content

        // Then
        uiStateBefore.cameraPosition.target shouldBe LatLng(latitude = 37.7749, longitude = -122.4194)
        uiStateAfter.cameraPosition.target shouldBe userLocation
    }

    private fun invokePrivate(subject: MapViewModel, methodName: String, vararg args: Any) {
        val parameterTypes = args.map { it::class.java }.toTypedArray()
        val method = MapViewModel::class.java.getDeclaredMethod(methodName, *parameterTypes)
        method.isAccessible = true
        method.invoke(subject, *args)
    }

    private fun createSubject(): MapViewModel {
        every { observePointsOfInterest.invoke() } returns flowOf(emptyList())
        every { observeExplorationProgress.invoke() } returns flowOf(
            ExplorationProgress(discovered = emptySet()),
        )
        every { observeSettings.invoke() } returns flowOf(
            AppSettings(
                distanceUnit = DistanceUnit.METRIC,
                selectedMapStyleId = "default",
            ),
        )
        every { getAllMapStyles.invoke() } returns MutableStateFlow(
            State.Content(
                listOf(
                    MapStyle.remote(
                        id = "default",
                        name = "Default",
                        value = "https://example.com/style.json",
                    ),
                ),
            ),
        )
        coEvery { discoverPointOfInterest.invoke(any()) } returns Unit

        return MapViewModel(
            observePointsOfInterest = observePointsOfInterest,
            observeExplorationProgress = observeExplorationProgress,
            observeSettings = observeSettings,
            getAllMapStyles = getAllMapStyles,
            discoverPointOfInterest = discoverPointOfInterest,
        )
    }
}
