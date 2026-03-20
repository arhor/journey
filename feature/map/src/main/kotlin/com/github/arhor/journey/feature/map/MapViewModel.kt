package com.github.arhor.journey.feature.map

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.github.arhor.journey.core.common.DomainError
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.core.common.fold
import com.github.arhor.journey.core.ui.MviViewModel
import com.github.arhor.journey.domain.model.ExplorationProgress
import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileGrid
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfig
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.PointOfInterest
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.StartExplorationTrackingSessionResult
import com.github.arhor.journey.domain.usecase.ClearExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.DiscoverPointOfInterestUseCase
import com.github.arhor.journey.domain.usecase.GetExplorationTileRuntimeConfigUseCase
import com.github.arhor.journey.domain.usecase.ObserveCollectibleResourceSpawnsUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationProgressUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.ObserveExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.ObservePointsOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveSelectedMapStyleUseCase
import com.github.arhor.journey.domain.usecase.SetExplorationTileCanonicalZoomUseCase
import com.github.arhor.journey.domain.usecase.SetExplorationTileRevealRadiusUseCase
import com.github.arhor.journey.domain.usecase.StartExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.StopExplorationTrackingSessionUseCase
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapObjectKind
import com.github.arhor.journey.feature.map.model.MapObjectUiModel
import com.github.arhor.journey.feature.map.renderer.FogOfWarRenderDataFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val DEFAULT_CAMERA_ZOOM = 17.0
private const val MAX_VISIBLE_FOG_TILE_COUNT = 8_192L
private const val MAX_BUFFERED_FOG_TILE_COUNT = MAX_VISIBLE_FOG_TILE_COUNT * 9

@Immutable
private data class State(
    val cameraPosition: CameraPositionState? = null,
    val cameraUpdateOrigin: CameraUpdateOrigin = CameraUpdateOrigin.PROGRAMMATIC,
    val isFollowingUserLocation: Boolean = true,
    val recenterRequestToken: Int = 0,
    val isAwaitingLocationPermissionResult: Boolean = false,
    val isDebugControlsSheetVisible: Boolean = false,
    val enabledDebugInfoItems: Set<MapDebugInfoItem> = emptySet(),
    val isFogOfWarOverlayEnabled: Boolean = true,
    val isTilesGridOverlayEnabled: Boolean = false,
    val canonicalZoom: Int,
    val revealRadiusMeters: Int,
    val mapRenderMode: MapRenderMode = MapRenderMode.Standard,
    val visibleBounds: GeoBounds? = null,
    val visibleTileRange: ExplorationTileRange? = null,
    val fogTileRange: ExplorationTileRange? = null,
    val visibleTileCount: Long = 0,
    val isFogSuppressedByVisibleTileLimit: Boolean = false,
    val failureMessage: String? = null,
)

@Stable
@HiltViewModel
class MapViewModel @Inject constructor(
    private val observePointsOfInterest: ObservePointsOfInterestUseCase,
    private val observeCollectibleResourceSpawns: ObserveCollectibleResourceSpawnsUseCase,
    private val observeExplorationProgress: ObserveExplorationProgressUseCase,
    private val observeExploredTiles: ObserveExploredTilesUseCase,
    private val observeSelectedMapStyle: ObserveSelectedMapStyleUseCase,
    private val discoverPointOfInterest: DiscoverPointOfInterestUseCase,
    private val clearExploredTiles: ClearExploredTilesUseCase,
    private val getExplorationTileRuntimeConfig: GetExplorationTileRuntimeConfigUseCase,
    private val setExplorationTileCanonicalZoom: SetExplorationTileCanonicalZoomUseCase,
    private val setExplorationTileRevealRadius: SetExplorationTileRevealRadiusUseCase,
    private val fogOfWarRenderDataFactory: FogOfWarRenderDataFactory,
    private val observeExplorationTrackingSession: ObserveExplorationTrackingSessionUseCase,
    private val startExplorationTrackingSession: StartExplorationTrackingSessionUseCase,
    private val stopExplorationTrackingSession: StopExplorationTrackingSessionUseCase,
) : MviViewModel<MapUiState, MapEffect, MapIntent>(
    initialState = MapUiState.Loading,
) {
    private val initialTileRuntimeConfig = getExplorationTileRuntimeConfig()
    private val _state = MutableStateFlow(
        State(
            canonicalZoom = initialTileRuntimeConfig.canonicalZoom,
            revealRadiusMeters = initialTileRuntimeConfig.revealRadiusMeters.toInt(),
        ),
    )
    private val trackingSession = observeExplorationTrackingSession()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ExplorationTrackingSession(),
        )

    override fun buildUiState(): Flow<MapUiState> =
        combine(
            combine(
                _state,
                trackingSession,
                observeSelectedMapStyle(),
                observePointsOfInterest(),
                observeExplorationProgress(),
            ) { state, session, mapStyleOutput, pointsOfInterest, explorationProgress ->
                UiStateInputs(
                    state = state,
                    trackingSession = session,
                    mapStyleOutput = mapStyleOutput,
                    pointsOfInterest = pointsOfInterest,
                    explorationProgress = explorationProgress,
                )
            },
            observeStateDerivedData(),
        ) { inputs, derivedData ->
            if (inputs.state.toStateDerivedKey() != derivedData.key) {
                null
            } else {
                intoUiState(
                    state = inputs.state,
                    trackingSession = inputs.trackingSession,
                    mapStyleOutput = inputs.mapStyleOutput,
                    pointsOfInterest = inputs.pointsOfInterest,
                    explorationProgress = inputs.explorationProgress,
                    fogOfWar = derivedData.fogOfWar,
                    resourceSpawns = derivedData.resourceSpawns,
                )
            }
        }
            .filterNotNull()
            .catch {
                emit(
                    MapUiState.Failure(
                        errorMessage = it.message ?: MAP_LOADING_FAILED_MESSAGE,
                    ),
                )
            }
            .distinctUntilChanged()

    private fun observeStateDerivedData(): Flow<StateDerivedData> =
        _state
            .map { it.toStateDerivedKey() }
            .distinctUntilChanged()
            .flatMapLatest { key ->
                combine(
                    observeFogExploredTiles(key.fogOfWarInputs),
                    observeVisibleResourceSpawns(key.visibleBounds),
                ) { exploredTiles, resourceSpawns ->
                    StateDerivedInputs(
                        key = key,
                        exploredTiles = exploredTiles,
                        resourceSpawns = resourceSpawns,
                    )
                }.mapLatest { inputs ->
                    StateDerivedData(
                        key = inputs.key,
                        fogOfWar = fogOfWarUiState(
                            inputs = inputs.key.fogOfWarInputs,
                            exploredTiles = inputs.exploredTiles,
                        ),
                        resourceSpawns = inputs.resourceSpawns,
                    )
                }
            }

    override suspend fun handleIntent(intent: MapIntent) {
        when (intent) {
            MapIntent.MapOpened -> onMapOpened()
            MapIntent.DebugControlsClicked -> onDebugControlsClicked()
            MapIntent.DebugControlsDismissed -> onDebugControlsDismissed()
            is MapIntent.DebugInfoVisibilityChanged -> onDebugInfoVisibilityChanged(intent)
            is MapIntent.FogOfWarOverlayToggled -> onFogOfWarOverlayToggled(intent)
            is MapIntent.TilesGridOverlayToggled -> onTilesGridOverlayToggled(intent)
            is MapIntent.CanonicalZoomChanged -> onCanonicalZoomChanged(intent)
            is MapIntent.RevealRadiusMetersChanged -> onRevealRadiusMetersChanged(intent)
            is MapIntent.MapRenderModeSelected -> onMapRenderModeSelected(intent)
            MapIntent.ResumeTrackingClicked -> onResumeTrackingClicked()
            MapIntent.StopTrackingClicked -> onStopTrackingClicked()
            is MapIntent.CameraViewportChanged -> onCameraViewportChanged(intent)
            is MapIntent.CameraGestureStarted -> onCameraGestureStarted(intent)
            is MapIntent.CameraSettled -> onCameraSettled(intent)
            is MapIntent.CurrentLocationUnavailable -> onCurrentLocationUnavailable()
            is MapIntent.LocationPermissionResult -> onLocationPermissionResult(intent)
            is MapIntent.MapTapped -> onMapTapped(intent)
            is MapIntent.RecenterClicked -> onRecenterClicked()
            is MapIntent.ObjectTapped -> onObjectTapped(intent.objectId)
            MapIntent.AddPoiClicked -> onAddPoiClicked()
            is MapIntent.ResetExploredTilesClicked -> onClearExploredTilesClicked()
            is MapIntent.MapLoadFailed -> onMapLoadFailed(intent)
        }
    }

    private fun observeFogExploredTiles(
        inputs: FogOfWarInputs,
    ): Flow<Set<ExplorationTile>> = inputs.fogTileRange
        ?.let(observeExploredTiles::invoke)
        ?: flowOf(emptySet())

    private fun observeVisibleResourceSpawns(
        visibleBounds: GeoBounds?,
    ): Flow<List<ResourceSpawn>> = visibleBounds
        ?.let(observeCollectibleResourceSpawns::invoke)
        ?: flowOf(emptyList())

    private suspend fun fogOfWarUiState(
        inputs: FogOfWarInputs,
        exploredTiles: Set<ExplorationTile>,
    ): FogOfWarUiState {
        val exploredVisibleTileCount = inputs.visibleTileRange?.let { visibleRange ->
            exploredTiles.count(visibleRange::contains)
        } ?: 0

        if (inputs.fogTileRange == null) {
            return FogOfWarUiState(
                canonicalZoom = inputs.canonicalZoom,
                visibleTileRange = inputs.visibleTileRange,
                fogRanges = emptyList(),
                renderData = null,
                visibleTileCount = inputs.visibleTileCount,
                exploredVisibleTileCount = exploredVisibleTileCount,
                isSuppressedByVisibleTileLimit = inputs.isSuppressedByVisibleTileLimit,
            )
        }

        // Fog geometry is CPU-bound; keep it off the main dispatcher.
        return withContext(Dispatchers.Default) {
            val coroutineContext = currentCoroutineContext()
            val checkCancelled = { coroutineContext.ensureActive() }
            val fogRanges = calculateUnexploredFogRanges(
                tileRange = inputs.fogTileRange,
                exploredTiles = exploredTiles,
                checkCancelled = checkCancelled,
            )
            val renderData = if (inputs.isFogOfWarOverlayEnabled) {
                fogOfWarRenderDataFactory.create(
                    fogRanges = fogRanges,
                    checkCancelled = checkCancelled,
                )
            } else {
                null
            }

            FogOfWarUiState(
                canonicalZoom = inputs.canonicalZoom,
                visibleTileRange = inputs.visibleTileRange,
                fogRanges = fogRanges,
                renderData = renderData,
                visibleTileCount = inputs.visibleTileCount,
                exploredVisibleTileCount = exploredVisibleTileCount,
                isSuppressedByVisibleTileLimit = inputs.isSuppressedByVisibleTileLimit,
            )
        }
    }

    private suspend fun onMapOpened() {
        startTrackingSessionIfNeeded()
    }

    private fun onDebugControlsClicked() {
        _state.update {
            it.copy(isDebugControlsSheetVisible = true)
        }
    }

    private fun onDebugControlsDismissed() {
        _state.update {
            it.copy(isDebugControlsSheetVisible = false)
        }
    }

    private fun onDebugInfoVisibilityChanged(intent: MapIntent.DebugInfoVisibilityChanged) {
        _state.update { state ->
            val enabledItems = state.enabledDebugInfoItems.toMutableSet().apply {
                if (intent.isVisible) {
                    add(intent.item)
                } else {
                    remove(intent.item)
                }
            }

            state.copy(enabledDebugInfoItems = enabledItems.toSet())
        }
    }

    private fun onFogOfWarOverlayToggled(intent: MapIntent.FogOfWarOverlayToggled) {
        _state.update {
            it.copy(isFogOfWarOverlayEnabled = intent.isEnabled)
        }
    }

    private fun onTilesGridOverlayToggled(intent: MapIntent.TilesGridOverlayToggled) {
        _state.update {
            it.copy(isTilesGridOverlayEnabled = intent.isEnabled)
        }
    }

    private fun onCanonicalZoomChanged(intent: MapIntent.CanonicalZoomChanged) {
        val canonicalZoom = intent.value.coerceIn(
            minimumValue = ExplorationTileRuntimeConfig.MIN_CANONICAL_ZOOM,
            maximumValue = ExplorationTileRuntimeConfig.MAX_CANONICAL_ZOOM,
        )
        setExplorationTileCanonicalZoom(canonicalZoom)

        _state.update { state ->
            val updatedState = state.copy(canonicalZoom = canonicalZoom)
            val visibleBounds = state.visibleBounds ?: return@update updatedState

            updatedState.withFogViewport(
                visibleBounds = visibleBounds,
                fogViewport = fogViewport(
                    visibleBounds = visibleBounds,
                    canonicalZoom = canonicalZoom,
                ),
            )
        }
    }

    private fun onRevealRadiusMetersChanged(intent: MapIntent.RevealRadiusMetersChanged) {
        val revealRadiusMeters = intent.value.coerceAtLeast(
            minimumValue = ExplorationTileRuntimeConfig.MIN_REVEAL_RADIUS_METERS.toInt(),
        )
        setExplorationTileRevealRadius(revealRadiusMeters.toDouble())

        _state.update {
            it.copy(revealRadiusMeters = revealRadiusMeters)
        }
    }

    private fun onMapRenderModeSelected(intent: MapIntent.MapRenderModeSelected) {
        _state.update {
            it.copy(mapRenderMode = intent.mode)
        }
    }

    private suspend fun onResumeTrackingClicked() {
        startTrackingSessionIfNeeded()
    }

    private suspend fun startTrackingSessionIfNeeded() {
        when (val result = startExplorationTrackingSession()) {
            StartExplorationTrackingSessionResult.AlreadyActive,
            StartExplorationTrackingSessionResult.Started -> Unit

            StartExplorationTrackingSessionResult.PermissionRequired -> {
                emitEffect(MapEffect.RequestLocationPermission)
            }

            is StartExplorationTrackingSessionResult.Failed -> {
                emitEffect(
                    MapEffect.ShowMessage(
                        result.message ?: TRACKING_START_FAILED_MESSAGE,
                    ),
                )
            }
        }
    }

    private suspend fun onStopTrackingClicked() {
        try {
            stopExplorationTrackingSession()
        } catch (e: Throwable) {
            emitEffect(MapEffect.ShowMessage(e.message ?: TRACKING_STOP_FAILED_MESSAGE))
        }
    }

    private fun intoUiState(
        state: State,
        trackingSession: ExplorationTrackingSession,
        mapStyleOutput: Output<MapStyle?, DomainError>,
        pointsOfInterest: List<PointOfInterest>,
        explorationProgress: ExplorationProgress,
        fogOfWar: FogOfWarUiState,
        resourceSpawns: List<ResourceSpawn>,
    ): MapUiState = if (state.failureMessage == null) {
        mapStyleOutput.fold(
            onSuccess = {
                val userLocation = trackingSession.lastKnownLocation?.toLatLng()
                val isCameraFollowingUserLocation = state.isFollowingUserLocation && userLocation != null
                val resolvedCameraPosition = if (isCameraFollowingUserLocation) {
                    userLocation.toCameraPosition(
                        zoom = state.cameraPosition?.zoom ?: DEFAULT_CAMERA_ZOOM,
                    )
                } else {
                    state.cameraPosition
                }
                    ?: userLocation?.toCameraPosition()
                    ?: pointsOfInterest.defaultCameraPosition()
                val resolvedCameraUpdateOrigin = if (isCameraFollowingUserLocation) {
                    CameraUpdateOrigin.PROGRAMMATIC
                } else {
                    state.cameraUpdateOrigin
                }

                MapUiState.Content(
                    cameraPosition = resolvedCameraPosition,
                    cameraUpdateOrigin = resolvedCameraUpdateOrigin,
                    recenterRequestToken = state.recenterRequestToken,
                    userLocation = userLocation,
                    isExplorationTrackingActive = trackingSession.isActive,
                    explorationTrackingCadence = trackingSession.cadence,
                    explorationTrackingStatus = trackingSession.status,
                    selectedStyle = it,
                    visibleObjects = mapObjects(
                        pointsOfInterest = pointsOfInterest,
                        explorationProgress = explorationProgress,
                        resourceSpawns = resourceSpawns,
                    ),
                    fogOfWar = fogOfWar,
                    debug = MapDebugUiState(
                        isSheetVisible = state.isDebugControlsSheetVisible,
                        enabledInfoItems = state.enabledDebugInfoItems,
                        isFogOfWarOverlayEnabled = state.isFogOfWarOverlayEnabled,
                        isTilesGridOverlayEnabled = state.isTilesGridOverlayEnabled,
                        canonicalZoom = state.canonicalZoom,
                        revealRadiusMeters = state.revealRadiusMeters,
                        renderMode = state.mapRenderMode,
                    ),
                )
            },
            onFailure = {
                MapUiState.Failure(
                    errorMessage = it.message
                        ?: it.cause?.message
                        ?: SETTINGS_LOADING_FAILED_MESSAGE,
                )
            },
        )
    } else {
        MapUiState.Failure(errorMessage = state.failureMessage)
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

    private suspend fun onLocationPermissionResult(intent: MapIntent.LocationPermissionResult) {
        val wasAwaitingLocationPermissionResult = _state.value.isAwaitingLocationPermissionResult

        _state.update {
            it.copy(isAwaitingLocationPermissionResult = false)
        }

        if (intent.isGranted) {
            startTrackingSessionIfNeeded()

            if (wasAwaitingLocationPermissionResult) {
                _state.update {
                    it.copy(
                        cameraUpdateOrigin = CameraUpdateOrigin.PROGRAMMATIC,
                        isFollowingUserLocation = true,
                        recenterRequestToken = it.recenterRequestToken + 1,
                    )
                }
            }
        } else {
            emitEffect(
                MapEffect.ShowMessage(
                    if (wasAwaitingLocationPermissionResult) {
                        LOCATION_PERMISSION_DENIED_MESSAGE
                    } else {
                        TRACKING_PERMISSION_REQUIRED_MESSAGE
                    },
                ),
            )
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
                    zoom = it.cameraPosition?.zoom ?: DEFAULT_CAMERA_ZOOM,
                ),
                cameraUpdateOrigin = CameraUpdateOrigin.PROGRAMMATIC,
                isFollowingUserLocation = false,
            )
        }
    }

    private fun onAddPoiClicked() {
        val target = (uiState.value as? MapUiState.Content)
            ?.cameraPosition
            ?.target
            ?: return

        emitEffect(
            MapEffect.OpenAddPoi(
                latitude = target.latitude,
                longitude = target.longitude,
            ),
        )
    }

    private fun onCameraViewportChanged(intent: MapIntent.CameraViewportChanged) {
        updateFogViewport(intent.visibleBounds)
    }

    private fun onCameraGestureStarted(intent: MapIntent.CameraGestureStarted) {
        _state.update { state ->
            if (
                state.cameraPosition == intent.position &&
                state.cameraUpdateOrigin == CameraUpdateOrigin.USER &&
                !state.isFollowingUserLocation
            ) {
                state
            } else {
                state.copy(
                    cameraPosition = intent.position,
                    cameraUpdateOrigin = CameraUpdateOrigin.USER,
                    isFollowingUserLocation = false,
                )
            }
        }
    }

    private fun onCameraSettled(intent: MapIntent.CameraSettled) {
        _state.update {
            it.copy(
                cameraPosition = intent.position,
                cameraUpdateOrigin = intent.origin,
                isFollowingUserLocation = if (intent.origin == CameraUpdateOrigin.USER) {
                    false
                } else {
                    it.isFollowingUserLocation
                },
            )
        }
    }

    private fun updateFogViewport(visibleBounds: GeoBounds) {
        val fogViewport = fogViewport(
            visibleBounds = visibleBounds,
            canonicalZoom = _state.value.canonicalZoom,
        )

        _state.update {
            it.withFogViewport(
                visibleBounds = visibleBounds,
                fogViewport = fogViewport,
            )
        }
    }

    private fun fogViewport(
        visibleBounds: GeoBounds,
        canonicalZoom: Int,
    ): FogViewport {
        val visibleTileRange = ExplorationTileGrid.tileRange(
            bounds = visibleBounds,
            zoom = canonicalZoom,
        )
        val visibleTileWidth = visibleTileRange.widthInTiles()
        val visibleTileHeight = visibleTileRange.heightInTiles()
        val fogTileRange = visibleTileRange.expandedBy(
            horizontalTilePadding = visibleTileWidth,
            verticalTilePadding = visibleTileHeight,
        )
        val visibleTileCount = visibleTileRange.tileCount
        val isSuppressedByVisibleTileLimit = visibleTileCount > MAX_VISIBLE_FOG_TILE_COUNT
        val isSuppressedByBufferedTileLimit = fogTileRange.tileCount > MAX_BUFFERED_FOG_TILE_COUNT

        return FogViewport(
            visibleTileRange = visibleTileRange,
            fogTileRange = fogTileRange,
            visibleTileCount = visibleTileCount,
            isSuppressedByVisibleTileLimit = isSuppressedByVisibleTileLimit || isSuppressedByBufferedTileLimit,
        )
    }

    private suspend fun onObjectTapped(objectId: String) {
        val contentState = uiState.value as? MapUiState.Content ?: return
        val objectUiModel = contentState.visibleObjects
            .firstOrNull { it.id == objectId }
            ?: return
        val parsedId = parseMapObjectId(objectUiModel.id) ?: return

        try {
            _state.update {
                it.copy(
                    cameraPosition = CameraPositionState(
                        target = objectUiModel.position,
                        zoom = it.cameraPosition?.zoom ?: DEFAULT_CAMERA_ZOOM,
                    ),
                    cameraUpdateOrigin = CameraUpdateOrigin.PROGRAMMATIC,
                    isFollowingUserLocation = false,
                )
            }

            if (parsedId.kind == MapObjectKind.PointOfInterest) {
                val poiId = parsedId.rawId.toLongOrNull() ?: return
                discoverPointOfInterest(poiId)
                emitEffect(MapEffect.OpenObjectDetails(parsedId.rawId))
            }
        } catch (e: Throwable) {
            emitEffect(MapEffect.ShowMessage(e.message ?: OBJECT_DISCOVERY_FAILED_MESSAGE))
        }
    }

    private suspend fun onClearExploredTilesClicked() {
        try {
            clearExploredTiles()
            emitEffect(MapEffect.ShowMessage(EXPLORATION_CLEAR_SUCCESS_MESSAGE))
        } catch (e: Throwable) {
            emitEffect(MapEffect.ShowMessage(e.message ?: EXPLORATION_CLEAR_FAILED_MESSAGE))
        }
    }

    private fun mapObjects(
        pointsOfInterest: List<PointOfInterest>,
        explorationProgress: ExplorationProgress,
        resourceSpawns: List<ResourceSpawn>,
    ): List<MapObjectUiModel> {
        val discoveredPoiIds = explorationProgress.discovered
            .map { it.poiId }
            .toSet()

        return pointsOfInterest.map { poi ->
            poi.toUiModel(isDiscovered = poi.id in discoveredPoiIds)
        } + resourceSpawns.map { it.toUiModel() }
    }

    private fun PointOfInterest.toUiModel(isDiscovered: Boolean): MapObjectUiModel =
        MapObjectUiModel(
            id = mapObjectId(
                kind = MapObjectKind.PointOfInterest,
                rawId = id.toString(),
            ),
            kind = MapObjectKind.PointOfInterest,
            title = name,
            description = description,
            position = location.toLatLng(),
            radiusMeters = radiusMeters,
            isDiscovered = isDiscovered,
        )

    private fun ResourceSpawn.toUiModel(): MapObjectUiModel =
        MapObjectUiModel(
            id = mapObjectId(
                kind = MapObjectKind.ResourceSpawn,
                rawId = id,
            ),
            kind = MapObjectKind.ResourceSpawn,
            title = typeId,
            description = null,
            position = position.toLatLng(),
            radiusMeters = collectionRadiusMeters.toInt(),
            isDiscovered = false,
            resourceType = ResourceType.fromTypeId(typeId),
        )

    private fun parseMapObjectId(id: String): ParsedMapObjectId? {
        val parts = id.split(MAP_OBJECT_ID_SEPARATOR, limit = 2)
        if (parts.size != 2) {
            return null
        }

        val kind = MapObjectKind.entries.firstOrNull { it.idPrefix == parts[0] } ?: return null
        return ParsedMapObjectId(
            kind = kind,
            rawId = parts[1],
        )
    }

    private fun mapObjectId(
        kind: MapObjectKind,
        rawId: String,
    ): String = "${kind.idPrefix}$MAP_OBJECT_ID_SEPARATOR$rawId"

    private fun GeoPoint.toLatLng(): LatLng =
        LatLng(
            latitude = lat,
            longitude = lon,
        )

    private fun LatLng.toCameraPosition(zoom: Double = DEFAULT_CAMERA_ZOOM): CameraPositionState =
        CameraPositionState(
            target = this,
            zoom = zoom,
        )

    private fun List<PointOfInterest>.defaultCameraPosition(): CameraPositionState? {
        if (isEmpty()) {
            return null
        }

        val minLatitude = minOf { it.location.lat }
        val maxLatitude = maxOf { it.location.lat }
        val minLongitude = minOf { it.location.lon }
        val maxLongitude = maxOf { it.location.lon }

        return CameraPositionState(
            target = LatLng(
                latitude = (minLatitude + maxLatitude) / 2.0,
                longitude = (minLongitude + maxLongitude) / 2.0,
            ),
            zoom = DEFAULT_CAMERA_ZOOM,
        )
    }

    @Immutable
    private data class FogViewport(
        val visibleTileRange: ExplorationTileRange,
        val fogTileRange: ExplorationTileRange,
        val visibleTileCount: Long,
        val isSuppressedByVisibleTileLimit: Boolean,
    )

    @Immutable
    private data class UiStateInputs(
        val state: State,
        val trackingSession: ExplorationTrackingSession,
        val mapStyleOutput: Output<MapStyle?, DomainError>,
        val pointsOfInterest: List<PointOfInterest>,
        val explorationProgress: ExplorationProgress,
    )

    @Immutable
    private data class FogOfWarInputs(
        val canonicalZoom: Int,
        val visibleTileRange: ExplorationTileRange?,
        val fogTileRange: ExplorationTileRange?,
        val visibleTileCount: Long,
        val isSuppressedByVisibleTileLimit: Boolean,
        val isFogOfWarOverlayEnabled: Boolean,
    )

    @Immutable
    private data class StateDerivedKey(
        val fogOfWarInputs: FogOfWarInputs,
        val visibleBounds: GeoBounds?,
    )

    @Immutable
    private data class StateDerivedInputs(
        val key: StateDerivedKey,
        val exploredTiles: Set<ExplorationTile>,
        val resourceSpawns: List<ResourceSpawn>,
    )

    @Immutable
    private data class StateDerivedData(
        val key: StateDerivedKey,
        val fogOfWar: FogOfWarUiState,
        val resourceSpawns: List<ResourceSpawn>,
    )

    @Immutable
    private data class ParsedMapObjectId(
        val kind: MapObjectKind,
        val rawId: String,
    )

    private fun State.matches(fogViewport: FogViewport): Boolean {
        return visibleTileRange == fogViewport.visibleTileRange
            && fogTileRange == fogViewport.fogTileRange
            && visibleTileCount == fogViewport.visibleTileCount
            && isFogSuppressedByVisibleTileLimit == fogViewport.isSuppressedByVisibleTileLimit
    }

    private fun State.withFogViewport(
        visibleBounds: GeoBounds,
        fogViewport: FogViewport,
    ): State {
        if (this.visibleBounds == visibleBounds && matches(fogViewport)) {
            return copy(visibleBounds = visibleBounds)
        }

        return copy(
            visibleBounds = visibleBounds,
            visibleTileRange = fogViewport.visibleTileRange,
            fogTileRange = fogViewport.fogTileRange,
            visibleTileCount = fogViewport.visibleTileCount,
            isFogSuppressedByVisibleTileLimit = fogViewport.isSuppressedByVisibleTileLimit,
        )
    }

    private fun State.toFogOfWarInputs(): FogOfWarInputs =
        FogOfWarInputs(
            canonicalZoom = canonicalZoom,
            visibleTileRange = visibleTileRange,
            fogTileRange = fogTileRange.takeUnless { isFogSuppressedByVisibleTileLimit },
            visibleTileCount = visibleTileCount,
            isSuppressedByVisibleTileLimit = isFogSuppressedByVisibleTileLimit,
            isFogOfWarOverlayEnabled = isFogOfWarOverlayEnabled,
        )

    private fun State.toStateDerivedKey(): StateDerivedKey =
        StateDerivedKey(
            fogOfWarInputs = toFogOfWarInputs(),
            visibleBounds = visibleBounds,
        )

    private fun ExplorationTileRange.widthInTiles(): Int = maxX - minX + 1

    private fun ExplorationTileRange.heightInTiles(): Int = maxY - minY + 1

    private companion object {
        const val MAP_LOADING_FAILED_MESSAGE = "Failed to load map state."
        const val SETTINGS_LOADING_FAILED_MESSAGE = "Failed to load settings state."
        const val OBJECT_DISCOVERY_FAILED_MESSAGE = "Failed to open map object details."
        const val MAP_STYLE_LOADING_FAILED_MESSAGE = "Failed to load map style."
        const val TRACKING_PERMISSION_REQUIRED_MESSAGE =
            "Location permission is required to start exploration tracking."
        const val LOCATION_PERMISSION_DENIED_MESSAGE =
            "Location permission is required to center the map on your position."
        const val CURRENT_LOCATION_UNAVAILABLE_MESSAGE =
            "Current location is not available yet."
        const val TRACKING_START_FAILED_MESSAGE =
            "Failed to start exploration tracking."
        const val TRACKING_STOP_FAILED_MESSAGE =
            "Failed to stop exploration tracking."
        const val EXPLORATION_CLEAR_SUCCESS_MESSAGE =
            "Explored tiles cleared."
        const val EXPLORATION_CLEAR_FAILED_MESSAGE =
            "Failed to clear explored tiles."
        const val MAP_OBJECT_ID_SEPARATOR = ":"
    }
}
