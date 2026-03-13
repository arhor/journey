package com.github.arhor.journey.ui.views.map

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.ExplorationProgress
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.PointOfInterest
import com.github.arhor.journey.domain.repository.MapStylesError
import com.github.arhor.journey.domain.usecase.DiscoverPointOfInterestUseCase
import com.github.arhor.journey.domain.usecase.GetAllMapStylesUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationProgressUseCase
import com.github.arhor.journey.domain.usecase.ObservePointsOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveSettingsUseCase
import com.github.arhor.journey.ui.MviViewModel
import com.github.arhor.journey.ui.views.map.model.CameraPositionState
import com.github.arhor.journey.ui.views.map.model.CameraUpdateOrigin
import com.github.arhor.journey.ui.views.map.model.LatLng
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
    val isAwaitingLocationPermissionResult: Boolean = false,
    val failureMessage: String? = null,
)

@Stable
@HiltViewModel
class MapViewModel @Inject constructor(
    private val observePointsOfInterest: ObservePointsOfInterestUseCase,
    private val observeExplorationProgress: ObserveExplorationProgressUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    private val getAllMapStyles: GetAllMapStylesUseCase,
    private val discoverPointOfInterest: DiscoverPointOfInterestUseCase,
) : MviViewModel<MapUiState, MapEffect, MapIntent>(
    initialState = MapUiState.Loading,
) {
    private val _state = MutableStateFlow(State())

    private data class SelectionState(
        val state: State,
        val settings: AppSettings,
        val availableStylesState: Output<List<MapStyle>, MapStylesError>,
    )

    private data class MapContentState(
        val pointsOfInterest: List<PointOfInterest>,
        val explorationProgress: ExplorationProgress,
    )

    override fun buildUiState(): Flow<MapUiState> =
        combine(
            combine(_state, observeSettings(), getAllMapStyles()) { state, settings, mapStylesState ->
                SelectionState(
                    state = state,
                    settings = settings,
                    availableStylesState = mapStylesState,
                )
            },
            combine(observePointsOfInterest(), observeExplorationProgress()) {
                    pointsOfInterest,
                    explorationProgress,
                ->
                MapContentState(
                    pointsOfInterest = pointsOfInterest,
                    explorationProgress = explorationProgress,
                )
            },
        ) { selectionState, mapContentState ->
            intoUiState(
                state = selectionState.state,
                settings = selectionState.settings,
                availableStylesState = selectionState.availableStylesState,
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
            is MapIntent.CurrentLocationUnavailable -> onCurrentLocationUnavailable()
            is MapIntent.LocationPermissionResult -> onLocationPermissionResult(intent)
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
            it.copy(isAwaitingLocationPermissionResult = true)
        }
        emitEffect(MapEffect.RequestLocationPermission)
    }

    private fun onLocationPermissionResult(intent: MapIntent.LocationPermissionResult) {
        val isAwaitingLocationPermissionResult = _state.value.isAwaitingLocationPermissionResult

        _state.update {
            it.copy(isAwaitingLocationPermissionResult = false)
        }

        if (!isAwaitingLocationPermissionResult) {
            return
        }

        if (intent.isGranted) {
            emitEffect(MapEffect.RecenterOnCurrentLocation)
        } else {
            emitEffect(MapEffect.ShowMessage(LOCATION_PERMISSION_DENIED_MESSAGE))
        }
    }

    private fun onCurrentLocationUnavailable() {
        emitEffect(MapEffect.ShowMessage(CURRENT_LOCATION_UNAVAILABLE_MESSAGE))
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
        availableStylesState: Output<List<MapStyle>, MapStylesError>,
        pointsOfInterest: List<PointOfInterest>,
        explorationProgress: ExplorationProgress,
    ): MapUiState {
        state.failureMessage?.let { errorMessage ->
            return MapUiState.Failure(errorMessage = errorMessage)
        }

        if (availableStylesState is Output.Failure) {
            return MapUiState.Failure(
                errorMessage = availableStylesState.error.message
                    ?: availableStylesState.error.cause?.message
                    ?: MAP_LOADING_FAILED_MESSAGE,
            )
        }

        val availableStyles = (availableStylesState as Output.Success).value

        return MapUiState.Content(
            cameraPosition = state.cameraPosition,
            cameraUpdateOrigin = state.cameraUpdateOrigin,
            selectedStyle = availableStyles.resolveSelectedStyle(settings.selectedMapStyleId),
            visibleObjects = mapObjects(pointsOfInterest, explorationProgress),
        )
    }

    private fun List<MapStyle>.resolveSelectedStyle(selectedMapStyleId: String?): MapStyle? =
        selectedMapStyleId
            ?.let { styleId -> firstOrNull { it.id == styleId } }
            ?: firstOrNull()

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
        const val LOCATION_PERMISSION_DENIED_MESSAGE =
            "Location permission is required to center the map on your position."
        const val CURRENT_LOCATION_UNAVAILABLE_MESSAGE =
            "Current location is not available yet."
    }
}
