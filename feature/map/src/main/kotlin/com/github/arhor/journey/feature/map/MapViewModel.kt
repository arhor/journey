package com.github.arhor.journey.feature.map

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.github.arhor.journey.core.common.DomainError
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.common.fold
import com.github.arhor.journey.core.ui.MviViewModel
import com.github.arhor.journey.domain.model.ExplorationProgress
import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileGrid
import com.github.arhor.journey.domain.model.ExplorationTilePrototype
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.PointOfInterest
import com.github.arhor.journey.domain.usecase.ClearExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.DiscoverPointOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationProgressUseCase
import com.github.arhor.journey.domain.usecase.ObserveExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.ObservePointsOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveSelectedMapStyleUseCase
import com.github.arhor.journey.domain.usecase.RevealExplorationTilesAtLocationUseCase
import com.github.arhor.journey.feature.map.location.ForegroundUserLocationTracker
import com.github.arhor.journey.feature.map.location.UserLocationUpdate
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapObjectUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private val DEFAULT_CAMERA_TARGET = LatLng(
    latitude = 37.7749,
    longitude = -122.4194,
)

private const val DEFAULT_ZOOM = 12.0
private const val MAX_VISIBLE_FOG_TILE_COUNT = 8_192L

@Immutable
private data class State(
    val cameraPosition: CameraPositionState = CameraPositionState(
        target = DEFAULT_CAMERA_TARGET,
        zoom = DEFAULT_ZOOM,
    ),
    val cameraUpdateOrigin: CameraUpdateOrigin = CameraUpdateOrigin.PROGRAMMATIC,
    val recenterRequestToken: Int = 0,
    val userLocation: LatLng? = null,
    val userLocationTrackingStatus: UserLocationTrackingStatus = UserLocationTrackingStatus.INACTIVE,
    val isAwaitingLocationPermissionResult: Boolean = false,
    val visibleTileRange: ExplorationTileRange? = null,
    val visibleTileCount: Long = 0,
    val isFogSuppressedByVisibleTileLimit: Boolean = false,
    val failureMessage: String? = null,
)

@Stable
@HiltViewModel
class MapViewModel @Inject constructor(
    private val observePointsOfInterest: ObservePointsOfInterestUseCase,
    private val observeExplorationProgress: ObserveExplorationProgressUseCase,
    private val observeExploredTiles: ObserveExploredTilesUseCase,
    private val observeSelectedMapStyle: ObserveSelectedMapStyleUseCase,
    private val discoverPointOfInterest: DiscoverPointOfInterestUseCase,
    private val revealExplorationTilesAtLocation: RevealExplorationTilesAtLocationUseCase,
    private val clearExploredTiles: ClearExploredTilesUseCase,
    private val foregroundUserLocationTracker: ForegroundUserLocationTracker,
) : MviViewModel<MapUiState, MapEffect, MapIntent>(
    initialState = MapUiState.Loading,
) {
    private val _state = MutableStateFlow(State())
    private var locationTrackingJob: Job? = null
    private var lastRevealedTiles: Set<ExplorationTile> = emptySet()

    override fun buildUiState(): Flow<MapUiState> = combine(
        _state,
        observeSelectedMapStyle(),
        observePointsOfInterest(),
        observeExplorationProgress(),
        observeVisibleExploredTiles(),
        ::intoUiState,
    ).catch {
        emit(
            MapUiState.Failure(
                errorMessage = it.message ?: MAP_LOADING_FAILED_MESSAGE,
            ),
        )
    }.distinctUntilChanged()

    override suspend fun handleIntent(intent: MapIntent) {
        when (intent) {
            is MapIntent.StartLocationTracking -> onStartLocationTracking()
            is MapIntent.StopLocationTracking -> onStopLocationTracking()
            is MapIntent.CameraSettled -> onCameraSettled(intent)
            is MapIntent.CurrentLocationUnavailable -> onCurrentLocationUnavailable()
            is MapIntent.LocationPermissionResult -> onLocationPermissionResult(intent)
            is MapIntent.MapTapped -> onMapTapped(intent)
            is MapIntent.RecenterClicked -> onRecenterClicked()
            is MapIntent.ObjectTapped -> onObjectTapped(intent.objectId)
            MapIntent.AddPoiClicked -> onAddPoiClicked()
            is MapIntent.ClearExploredTilesClicked -> onClearExploredTilesClicked()
            is MapIntent.MapLoadFailed -> onMapLoadFailed(intent)
        }
    }

    /* ------------------------------------------ Internal implementation ------------------------------------------- */

    private fun observeVisibleExploredTiles(): Flow<Set<ExplorationTile>> =
        _state
            .map { state ->
                state.visibleTileRange.takeUnless { state.isFogSuppressedByVisibleTileLimit }
            }
            .distinctUntilChanged()
            .flatMapLatest { range ->
                range?.let(observeExploredTiles::invoke) ?: flowOf(emptySet())
            }

    private fun onStartLocationTracking() {
        if (locationTrackingJob != null) {
            return
        }

        locationTrackingJob = viewModelScope.launch {
            foregroundUserLocationTracker.observeLocations()
                .catch {
                    emitEffect(MapEffect.ShowMessage(it.message ?: LOCATION_TRACKING_FAILED_MESSAGE))
                }
                .collect { update ->
                    when (update) {
                        is UserLocationUpdate.Available -> onUserLocationAvailable(update.location)

                        UserLocationUpdate.LocationServicesDisabled -> {
                            _state.update {
                                it.copy(userLocationTrackingStatus = UserLocationTrackingStatus.LOCATION_SERVICES_DISABLED)
                            }
                        }

                        UserLocationUpdate.PermissionDenied -> {
                            _state.update {
                                it.copy(userLocationTrackingStatus = UserLocationTrackingStatus.PERMISSION_DENIED)
                            }
                        }

                        UserLocationUpdate.TemporarilyUnavailable -> {
                            _state.update {
                                it.copy(userLocationTrackingStatus = UserLocationTrackingStatus.TEMPORARILY_UNAVAILABLE)
                            }
                        }
                    }
                }
        }
    }

    private suspend fun onUserLocationAvailable(location: GeoPoint) {
        _state.update {
            it.copy(
                userLocation = location.toLatLng(),
                userLocationTrackingStatus = UserLocationTrackingStatus.TRACKING,
            )
        }

        val revealTiles = ExplorationTileGrid.revealTilesAround(location)
        if (revealTiles == lastRevealedTiles) {
            return
        }

        try {
            revealExplorationTilesAtLocation(location)
            lastRevealedTiles = revealTiles
        } catch (e: Throwable) {
            emitEffect(MapEffect.ShowMessage(e.message ?: EXPLORATION_PERSIST_FAILED_MESSAGE))
        }
    }

    private fun onStopLocationTracking() {
        locationTrackingJob?.cancel()
        locationTrackingJob = null

        _state.update {
            it.copy(userLocationTrackingStatus = UserLocationTrackingStatus.INACTIVE)
        }
    }

    private fun intoUiState(
        state: State,
        mapStyleOutput: Output<MapStyle?, DomainError>,
        pointsOfInterest: List<PointOfInterest>,
        explorationProgress: ExplorationProgress,
        exploredTiles: Set<ExplorationTile>,
    ): MapUiState = if (state.failureMessage == null) {
        mapStyleOutput.fold(
            onSuccess = {
                val resolvedCameraPosition = state.cameraPosition.centerOn(pointsOfInterest)

                MapUiState.Content(
                    cameraPosition = resolvedCameraPosition,
                    cameraUpdateOrigin = state.cameraUpdateOrigin,
                    recenterRequestToken = state.recenterRequestToken,
                    userLocation = state.userLocation,
                    userLocationTrackingStatus = state.userLocationTrackingStatus,
                    selectedStyle = it,
                    visibleObjects = mapObjects(pointsOfInterest, explorationProgress),
                    fogOfWar = fogOfWarUiState(
                        state = state,
                        exploredTiles = exploredTiles,
                    ),
                )
            },
            onFailure = {
                MapUiState.Failure(
                    errorMessage = it.message
                        ?: it.cause?.message
                        ?: SETTINGS_LOADING_FAILED_MESSAGE,
                )
            }
        )
    } else {
        MapUiState.Failure(errorMessage = state.failureMessage)
    }

    private fun fogOfWarUiState(
        state: State,
        exploredTiles: Set<ExplorationTile>,
    ): FogOfWarUiState {
        val fogRanges = calculateUnexploredFogRanges(
            visibleRange = state.visibleTileRange.takeUnless { state.isFogSuppressedByVisibleTileLimit },
            exploredTiles = exploredTiles,
        )

        return FogOfWarUiState(
            canonicalZoom = ExplorationTilePrototype.CANONICAL_ZOOM,
            fogRanges = fogRanges,
            visibleTileCount = state.visibleTileCount,
            exploredVisibleTileCount = exploredTiles.size,
            isSuppressedByVisibleTileLimit = state.isFogSuppressedByVisibleTileLimit,
        )
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
            _state.update {
                it.copy(recenterRequestToken = it.recenterRequestToken + 1)
            }

            onStopLocationTracking()
            onStartLocationTracking()
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

    private fun onAddPoiClicked() {
        val target = _state.value.cameraPosition.target
        emitEffect(
            MapEffect.OpenAddPoi(
                latitude = target.latitude,
                longitude = target.longitude,
            ),
        )
    }

    private fun onCameraSettled(intent: MapIntent.CameraSettled) {
        val fogViewport = fogViewport(intent.visibleBounds)

        _state.update {
            it.copy(
                cameraPosition = intent.position,
                cameraUpdateOrigin = intent.origin,
                visibleTileRange = fogViewport.visibleTileRange,
                visibleTileCount = fogViewport.visibleTileCount,
                isFogSuppressedByVisibleTileLimit = fogViewport.isSuppressedByVisibleTileLimit,
            )
        }
    }

    private fun fogViewport(visibleBounds: GeoBounds?): FogViewport {
        val visibleTileRange = visibleBounds?.let {
            ExplorationTileGrid.tileRange(
                bounds = it,
                zoom = ExplorationTilePrototype.CANONICAL_ZOOM,
            )
        }
        val visibleTileCount = visibleTileRange?.tileCount ?: 0

        return FogViewport(
            visibleTileRange = visibleTileRange,
            visibleTileCount = visibleTileCount,
            isSuppressedByVisibleTileLimit = visibleTileCount > MAX_VISIBLE_FOG_TILE_COUNT,
        )
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

    private suspend fun onClearExploredTilesClicked() {
        try {
            clearExploredTiles()
            lastRevealedTiles = emptySet()
            emitEffect(MapEffect.ShowMessage(EXPLORATION_CLEAR_SUCCESS_MESSAGE))
        } catch (e: Throwable) {
            emitEffect(MapEffect.ShowMessage(e.message ?: EXPLORATION_CLEAR_FAILED_MESSAGE))
        }
    }

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

    private fun CameraPositionState.centerOn(pointsOfInterest: List<PointOfInterest>): CameraPositionState {
        if (
            target != DEFAULT_CAMERA_TARGET ||
            pointsOfInterest.isEmpty()
        ) {
            return this
        }

        val minLatitude = pointsOfInterest.minOf { it.location.lat }
        val maxLatitude = pointsOfInterest.maxOf { it.location.lat }
        val minLongitude = pointsOfInterest.minOf { it.location.lon }
        val maxLongitude = pointsOfInterest.maxOf { it.location.lon }

        return copy(
            target = LatLng(
                latitude = (minLatitude + maxLatitude) / 2.0,
                longitude = (minLongitude + maxLongitude) / 2.0,
            ),
        )
    }

    @Immutable
    private data class FogViewport(
        val visibleTileRange: ExplorationTileRange?,
        val visibleTileCount: Long,
        val isSuppressedByVisibleTileLimit: Boolean,
    )

    private companion object {
        const val MAP_LOADING_FAILED_MESSAGE = "Failed to load map state."
        const val SETTINGS_LOADING_FAILED_MESSAGE = "Failed to load settings state."
        const val OBJECT_DISCOVERY_FAILED_MESSAGE = "Failed to open map object details."
        const val MAP_STYLE_LOADING_FAILED_MESSAGE = "Failed to load map style."
        const val LOCATION_PERMISSION_DENIED_MESSAGE =
            "Location permission is required to center the map on your position."
        const val CURRENT_LOCATION_UNAVAILABLE_MESSAGE =
            "Current location is not available yet."
        const val LOCATION_TRACKING_FAILED_MESSAGE =
            "Foreground location tracking failed."
        const val EXPLORATION_PERSIST_FAILED_MESSAGE =
            "Failed to persist explored tiles."
        const val EXPLORATION_CLEAR_SUCCESS_MESSAGE =
            "Explored tiles cleared."
        const val EXPLORATION_CLEAR_FAILED_MESSAGE =
            "Failed to clear explored tiles."
    }
}
