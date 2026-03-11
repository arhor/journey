package com.github.arhor.journey.ui.views.map

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.github.arhor.journey.ui.views.map.model.CameraPositionState
import com.github.arhor.journey.domain.exploration.model.ExplorationProgress
import com.github.arhor.journey.domain.exploration.model.GeoPoint
import com.github.arhor.journey.domain.map.model.ResolvedMapStyle
import com.github.arhor.journey.domain.map.model.MapStyle
import com.github.arhor.journey.domain.exploration.model.PointOfInterest
import com.github.arhor.journey.domain.settings.model.AppSettings
import com.github.arhor.journey.domain.usecase.DiscoverPointOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveMapStylesUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationProgressUseCase
import com.github.arhor.journey.domain.usecase.ObservePointsOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveSettingsUseCase
import com.github.arhor.journey.domain.usecase.ObserveSelectedStyleUseCase
import com.github.arhor.journey.ui.MviViewModel
import com.github.arhor.journey.ui.views.map.model.LatLng
import com.github.arhor.journey.ui.views.map.model.CameraUpdateOrigin
import com.github.arhor.journey.ui.views.map.model.MapObjectUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import javax.inject.Inject

private val DEFAULT_CAMERA_TARGET = LatLng(
    latitude = 37.7749,
    longitude = -122.4194,
)

private const val DEFAULT_ZOOM = 12.0

@Immutable
private data class State(
    val cameraPosition: CameraPositionState = CameraPositionState(
        target = DEFAULT_CAMERA_TARGET,
        zoom = DEFAULT_ZOOM,
    ),
    val cameraUpdateOrigin: CameraUpdateOrigin = CameraUpdateOrigin.PROGRAMMATIC,
    val failureMessage: String? = null,
)

@Stable
@HiltViewModel
class MapViewModel @Inject constructor(
    private val observePointsOfInterest: ObservePointsOfInterestUseCase,
    private val observeExplorationProgress: ObserveExplorationProgressUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    private val observeAvailableMapStyles: ObserveMapStylesUseCase,
    private val discoverPointOfInterest: DiscoverPointOfInterestUseCase,
    private val observeSelectedStyle: ObserveSelectedStyleUseCase,
) : MviViewModel<MapUiState, MapEffect, MapIntent>(
    initialState = MapUiState.Loading,
) {
    private val _state = MutableStateFlow(State())

    private data class SelectionState(
        val state: State,
        val settings: AppSettings,
        val availableStyles: List<MapStyle>,
    )

    private data class MapContentState(
        val resolvedStyle: ResolvedMapStyle,
        val pointsOfInterest: List<PointOfInterest>,
        val explorationProgress: ExplorationProgress,
    )

    override fun buildUiState(): Flow<MapUiState> =
        combine(
            combine(_state, observeSettings(), observeAvailableMapStyles()) { state, settings, availableStyles ->
                SelectionState(
                    state = state,
                    settings = settings,
                    availableStyles = availableStyles,
                )
            },
            combine(observeSelectedStyle(), observePointsOfInterest(), observeExplorationProgress()) {
                    resolvedStyle,
                    pointsOfInterest,
                    explorationProgress,
                ->
                MapContentState(
                    resolvedStyle = resolvedStyle,
                    pointsOfInterest = pointsOfInterest,
                    explorationProgress = explorationProgress,
                )
            },
        ) { selectionState, mapContentState ->
            intoUiState(
                state = selectionState.state,
                settings = selectionState.settings,
                availableStyles = selectionState.availableStyles,
                resolvedStyle = mapContentState.resolvedStyle,
                pointsOfInterest = mapContentState.pointsOfInterest,
                explorationProgress = mapContentState.explorationProgress,
            )
        }.catch {
            emit(
                MapUiState.Failure(
                    errorMessage = it.message ?: MAP_LOADING_FAILED_MESSAGE,
                ),
            )
        }.distinctUntilChanged()

    override suspend fun handleIntent(intent: MapIntent) {
        when (intent) {
            is MapIntent.CameraSettled -> onCameraSettled(intent)
            is MapIntent.MapTapped -> onMapTapped(intent)
            is MapIntent.RecenterClicked -> onRecenterClicked()
            is MapIntent.ObjectTapped -> onObjectTapped(intent.objectId)
            is MapIntent.MapLoadFailed -> onMapLoadFailed(intent)
        }
    }

    private fun onMapLoadFailed(intent: MapIntent.MapLoadFailed) {
        _state.update {
            it.copy(failureMessage = intent.message ?: MAP_STYLE_LOADING_FAILED_MESSAGE)
        }
    }

    private fun onRecenterClicked() {
        _state.update {
            it.copy(
                cameraPosition = CameraPositionState(
                    target = DEFAULT_CAMERA_TARGET,
                    zoom = DEFAULT_ZOOM,
                ),
                cameraUpdateOrigin = CameraUpdateOrigin.PROGRAMMATIC,
            )
        }
        emitEffect(MapEffect.RequestLocationPermission)
    }

    private fun onMapTapped(intent: MapIntent.MapTapped) {
        _state.update {
            it.copy(
                cameraPosition = CameraPositionState(
                    target = intent.target,
                    zoom = it.cameraPosition.zoom,
                ),
                cameraUpdateOrigin = CameraUpdateOrigin.PROGRAMMATIC,
            )
        }
    }

    private fun onCameraSettled(intent: MapIntent.CameraSettled) {
        _state.update {
            it.copy(
                cameraPosition = intent.position,
                cameraUpdateOrigin = intent.origin,
            )
        }
    }

    private suspend fun onObjectTapped(objectId: String) {
        val contentState = uiState.value as? MapUiState.Content ?: return

        try {
            contentState.visibleObjects
                .firstOrNull { it.id == objectId }
                ?.let { objectUiModel ->
                    _state.update {
                        it.copy(
                            cameraPosition = CameraPositionState(
                                target = objectUiModel.position,
                                zoom = it.cameraPosition.zoom,
                            ),
                            cameraUpdateOrigin = CameraUpdateOrigin.PROGRAMMATIC,
                        )
                    }
                }

            discoverPointOfInterest(objectId)
            emitEffect(MapEffect.OpenObjectDetails(objectId))
        } catch (e: Throwable) {
            emitEffect(MapEffect.ShowMessage(e.message ?: OBJECT_DISCOVERY_FAILED_MESSAGE))
        }
    }

    private fun intoUiState(
        state: State,
        settings: AppSettings,
        availableStyles: List<MapStyle>,
        resolvedStyle: ResolvedMapStyle,
        pointsOfInterest: List<PointOfInterest>,
        explorationProgress: ExplorationProgress,
    ): MapUiState {
        state.failureMessage?.let { errorMessage ->
            return MapUiState.Failure(errorMessage = errorMessage)
        }

        return MapUiState.Content(
            cameraPosition = state.cameraPosition,
            cameraUpdateOrigin = state.cameraUpdateOrigin,
            selectedStyle = availableStyles.resolveSelectedStyle(settings.selectedMapStyleId),
            resolvedStyle = resolvedStyle,
            visibleObjects = mapObjects(pointsOfInterest, explorationProgress),
        )
    }

    private fun List<MapStyle>.resolveSelectedStyle(selectedMapStyleId: String): MapStyle =
        firstOrNull { it.id == selectedMapStyleId } ?: first { it.id == MapStyle.DEFAULT_ID }

    private fun mapObjects(
        pointsOfInterest: List<PointOfInterest>,
        explorationProgress: ExplorationProgress,
    ): List<MapObjectUiModel> {
        val discoveredPoiIds = explorationProgress.discovered
            .map { it.poiId }
            .toSet()

        return pointsOfInterest.map { poi ->
            poi.toUiModel(isDiscovered = poi.id in discoveredPoiIds)
        }
    }

    private fun PointOfInterest.toUiModel(isDiscovered: Boolean): MapObjectUiModel =
        MapObjectUiModel(
            id = id,
            title = name,
            description = description,
            position = location.toLatLng(),
            radiusMeters = radiusMeters,
            isDiscovered = isDiscovered,
        )

    private fun GeoPoint.toLatLng(): LatLng =
        LatLng(
            latitude = lat,
            longitude = lon,
        )

    private companion object {
        const val MAP_LOADING_FAILED_MESSAGE = "Failed to load map state."
        const val OBJECT_DISCOVERY_FAILED_MESSAGE = "Failed to open map object details."
        const val MAP_STYLE_LOADING_FAILED_MESSAGE = "Failed to load map style."
    }
}
