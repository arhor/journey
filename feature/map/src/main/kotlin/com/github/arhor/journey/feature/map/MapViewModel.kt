package com.github.arhor.journey.feature.map

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.github.arhor.journey.core.common.DomainError
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.core.common.fold
import com.github.arhor.journey.core.ui.MviViewModel
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
import com.github.arhor.journey.domain.usecase.ObservePointsOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveSelectedMapStyleUseCase
import com.github.arhor.journey.domain.usecase.SetExplorationTileCanonicalZoomUseCase
import com.github.arhor.journey.domain.usecase.SetExplorationTileRevealRadiusUseCase
import com.github.arhor.journey.domain.usecase.StartExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.StopExplorationTrackingSessionUseCase
import com.github.arhor.journey.feature.map.fow.FogOfWarController
import com.github.arhor.journey.feature.map.fow.model.FogOfWarUiState
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapObjectKind
import com.github.arhor.journey.feature.map.model.MapObjectUiModel
import com.github.arhor.journey.feature.map.model.MapViewportSize
import com.github.arhor.journey.feature.map.prewarm.MapTilePrewarmRequest
import com.github.arhor.journey.feature.map.prewarm.MapTilePrewarmer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

private const val DEFAULT_CAMERA_ZOOM = 17.0
private const val RESOURCE_QUERY_BUFFER_FRACTION = 0.5
private const val MIN_LONGITUDE = -180.0
private const val MAX_LONGITUDE = 180.0 - 1e-9
private const val MIN_LATITUDE = -85.05112878
private const val MAX_LATITUDE = 85.05112878
private const val CAMERA_PREWARM_REQUEST_KEY = "main-map-camera"
private const val STATIC_CAMERA_PREWARM_SAMPLE_COUNT = 1
private const val ANIMATED_CAMERA_PREWARM_SAMPLE_COUNT = 4
private const val STATIC_CAMERA_PREWARM_BURST_LIMIT = 48
private const val ANIMATED_CAMERA_PREWARM_BURST_LIMIT = 96
private val USER_LOCATION_RECENTER_PREWARM_DURATION = 600.milliseconds

@Immutable
private data class State(
    val cameraPosition: CameraPositionState? = null,
    val cameraUpdateOrigin: CameraUpdateOrigin = CameraUpdateOrigin.PROGRAMMATIC,
    val isFollowingUserLocation: Boolean = true,
    val recenterRequestToken: Int = 0,
    val isAwaitingLocationPermissionResult: Boolean = false,
    val isDebugControlsSheetVisible: Boolean = false,
    val enabledDebugInfoItems: Set<MapDebugInfoItem> = emptySet(),
    val isTilesGridOverlayEnabled: Boolean = false,
    val canonicalZoom: Int,
    val revealRadiusMeters: Int,
    val mapRenderMode: MapRenderMode = MapRenderMode.Standard,
    val visibleBounds: GeoBounds? = null,
    val viewportSize: MapViewportSize? = null,
    val resourceQueryBounds: GeoBounds? = null,
    val failureMessage: String? = null,
)

@Stable
@HiltViewModel
class MapViewModel @Inject constructor(
    private val observePointsOfInterest: ObservePointsOfInterestUseCase,
    private val observeCollectibleResourceSpawns: ObserveCollectibleResourceSpawnsUseCase,
    private val observeExplorationProgress: ObserveExplorationProgressUseCase,
    private val observeSelectedMapStyle: ObserveSelectedMapStyleUseCase,
    private val discoverPointOfInterest: DiscoverPointOfInterestUseCase,
    private val clearExploredTiles: ClearExploredTilesUseCase,
    private val getExplorationTileRuntimeConfig: GetExplorationTileRuntimeConfigUseCase,
    private val setExplorationTileCanonicalZoom: SetExplorationTileCanonicalZoomUseCase,
    private val setExplorationTileRevealRadius: SetExplorationTileRevealRadiusUseCase,
    private val fogOfWarController: FogOfWarController,
    private val observeExplorationTrackingSession: ObserveExplorationTrackingSessionUseCase,
    private val startExplorationTrackingSession: StartExplorationTrackingSessionUseCase,
    private val stopExplorationTrackingSession: StopExplorationTrackingSessionUseCase,
    private val mapTilePrewarmer: MapTilePrewarmer,
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
    private var cachedVisibleObjects: List<MapObjectUiModel> = emptyList()

    init {
        fogOfWarController.attach(viewModelScope)
        fogOfWarController.setCanonicalZoom(initialTileRuntimeConfig.canonicalZoom)
    }

    override fun buildUiState(): Flow<MapUiState> =
        combine(
            combine(
                _state,
                fogOfWarController.uiState,
                trackingSession,
                observeSelectedMapStyle(),
                observePointsOfInterest(),
            ) { state, fogOfWar, session, mapStyleOutput, pointsOfInterest ->
                UiStateInputs(
                    state = state,
                    fogOfWar = fogOfWar,
                    trackingSession = session,
                    mapStyleOutput = mapStyleOutput,
                    pointsOfInterest = pointsOfInterest,
                )
            },
            observeVisibleMapObjects(),
        ) { inputs, visibleObjects ->
            intoUiState(
                state = inputs.state,
                trackingSession = inputs.trackingSession,
                mapStyleOutput = inputs.mapStyleOutput,
                pointsOfInterest = inputs.pointsOfInterest,
                fogOfWar = inputs.fogOfWar,
                visibleObjects = visibleObjects,
            )
        }
            .catch {
                emit(
                    MapUiState.Failure(
                        errorMessage = it.message ?: MAP_LOADING_FAILED_MESSAGE,
                    ),
                )
            }
            .distinctUntilChanged()

    private fun observeVisibleMapObjects(): Flow<List<MapObjectUiModel>> =
        combine(
            observePointOfInterestObjects(),
            observeVisibleResourceSpawnObjects(),
        ) { pointOfInterestObjects, resourceSpawnObjects ->
            pointOfInterestObjects + resourceSpawnObjects
        }
            .distinctUntilChanged()

    private fun observePointOfInterestObjects(): Flow<List<MapObjectUiModel>> =
        combine(
            observePointsOfInterest(),
            observeExplorationProgress(),
        ) { pointsOfInterest, explorationProgress ->
            val discoveredPoiIds = explorationProgress.discovered
                .mapTo(mutableSetOf()) { it.poiId }

            pointsOfInterest.map { pointOfInterest ->
                pointOfInterest.toUiModel(isDiscovered = pointOfInterest.id in discoveredPoiIds)
            }
        }
            .distinctUntilChanged()

    private fun observeVisibleResourceSpawnObjects(): Flow<List<MapObjectUiModel>> =
        observeResourceDerivedData()
            .map { resourceDerivedData ->
                resourceDerivedData.resourceSpawns.map { resourceSpawn ->
                    resourceSpawn.toUiModel()
                }
            }
            .distinctUntilChanged()

    private fun observeResourceDerivedData(): Flow<ResourceDerivedData> =
        _state
            .map { state -> state.resourceQueryBounds }
            .distinctUntilChanged()
            .flatMapLatest { queryBounds ->
                observeVisibleResourceSpawns(queryBounds)
                    .map { resourceSpawns ->
                        ResourceDerivedData(
                            queryBounds = queryBounds,
                            resourceSpawns = resourceSpawns,
                        )
                    }
            }

    override suspend fun handleIntent(intent: MapIntent) {
        when (intent) {
            is MapIntent.MapOpened -> onMapOpened()
            is MapIntent.DebugControlsClicked -> onDebugControlsClicked()
            is MapIntent.DebugControlsDismissed -> onDebugControlsDismissed()
            is MapIntent.DebugInfoVisibilityChanged -> onDebugInfoVisibilityChanged(intent)
            is MapIntent.FogOfWarOverlayToggled -> onFogOfWarOverlayToggled(intent)
            is MapIntent.TilesGridOverlayToggled -> onTilesGridOverlayToggled(intent)
            is MapIntent.CanonicalZoomChanged -> onCanonicalZoomChanged(intent)
            is MapIntent.RevealRadiusMetersChanged -> onRevealRadiusMetersChanged(intent)
            is MapIntent.MapRenderModeSelected -> onMapRenderModeSelected(intent)
            is MapIntent.ResumeTrackingClicked -> onResumeTrackingClicked()
            is MapIntent.StopTrackingClicked -> onStopTrackingClicked()
            is MapIntent.CameraViewportChanged -> onCameraViewportChanged(intent)
            is MapIntent.MapViewportSizeChanged -> onMapViewportSizeChanged(intent)
            is MapIntent.CameraGestureStarted -> onCameraGestureStarted(intent)
            is MapIntent.CameraSettled -> onCameraSettled(intent)
            is MapIntent.CurrentLocationUnavailable -> onCurrentLocationUnavailable()
            is MapIntent.LocationPermissionResult -> onLocationPermissionResult(intent)
            is MapIntent.MapTapped -> onMapTapped(intent)
            is MapIntent.RecenterClicked -> onRecenterClicked()
            is MapIntent.ObjectTapped -> onObjectTapped(intent.objectId)
            is MapIntent.AddPoiClicked -> onAddPoiClicked()
            is MapIntent.ResetExploredTilesClicked -> onClearExploredTilesClicked()
            is MapIntent.MapLoadFailed -> onMapLoadFailed(intent)
        }
    }
    private fun observeVisibleResourceSpawns(
        queryBounds: GeoBounds?,
    ): Flow<List<ResourceSpawn>> = queryBounds
        ?.let(observeCollectibleResourceSpawns::invoke)
        ?: flowOf(emptyList())

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
        fogOfWarController.setOverlayEnabled(intent.isEnabled)
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
            state.copy(canonicalZoom = canonicalZoom)
        }

        fogOfWarController.setCanonicalZoom(canonicalZoom)
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
        fogOfWar: FogOfWarUiState,
        visibleObjects: List<MapObjectUiModel>,
    ): MapUiState = if (state.failureMessage == null) {
        val resolvedVisibleObjects = reuseVisibleObjects(visibleObjects)

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
                    visibleObjects = resolvedVisibleObjects,
                    fogOfWar = fogOfWar,
                    debug = MapDebugUiState(
                        isSheetVisible = state.isDebugControlsSheetVisible,
                        enabledInfoItems = state.enabledDebugInfoItems,
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
                trackingSession.value.lastKnownLocation
                    ?.toLatLng()
                    ?.toCameraPosition(
                        zoom = (uiState.value as? MapUiState.Content)
                            ?.cameraPosition
                            ?.zoom
                            ?: DEFAULT_CAMERA_ZOOM,
                    )
                    ?.let { targetCamera ->
                        requestTilePrewarm(
                            targetCamera = targetCamera,
                            animationDuration = USER_LOCATION_RECENTER_PREWARM_DURATION,
                            sampleCount = ANIMATED_CAMERA_PREWARM_SAMPLE_COUNT,
                            burstLimit = ANIMATED_CAMERA_PREWARM_BURST_LIMIT,
                        )
                    }

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
        requestTilePrewarm(
            targetCamera = CameraPositionState(
                target = intent.target,
                zoom = (uiState.value as? MapUiState.Content)
                    ?.cameraPosition
                    ?.zoom
                    ?: DEFAULT_CAMERA_ZOOM,
            ),
            animationDuration = kotlin.time.Duration.ZERO,
            sampleCount = STATIC_CAMERA_PREWARM_SAMPLE_COUNT,
            burstLimit = STATIC_CAMERA_PREWARM_BURST_LIMIT,
        )

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
        _state.update { state ->
            state.copy(
                visibleBounds = intent.visibleBounds,
                resourceQueryBounds = resolveResourceQueryBounds(
                    visibleBounds = intent.visibleBounds,
                    currentQueryBounds = state.resourceQueryBounds,
                ),
            )
        }

        fogOfWarController.updateViewport(intent.visibleBounds)
    }

    private fun onMapViewportSizeChanged(intent: MapIntent.MapViewportSizeChanged) {
        _state.update { state ->
            if (state.viewportSize == intent.viewportSize) {
                state
            } else {
                state.copy(viewportSize = intent.viewportSize)
            }
        }
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

    private suspend fun onObjectTapped(objectId: String) {
        val contentState = uiState.value as? MapUiState.Content ?: return
        val objectUiModel = contentState.visibleObjects
            .firstOrNull { it.id == objectId }
            ?: return
        val parsedId = parseMapObjectId(objectUiModel.id) ?: return

        try {
            requestTilePrewarm(
                targetCamera = CameraPositionState(
                    target = objectUiModel.position,
                    zoom = contentState.cameraPosition?.zoom ?: DEFAULT_CAMERA_ZOOM,
                ),
                animationDuration = kotlin.time.Duration.ZERO,
                sampleCount = STATIC_CAMERA_PREWARM_SAMPLE_COUNT,
                burstLimit = STATIC_CAMERA_PREWARM_BURST_LIMIT,
            )

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

    private fun requestTilePrewarm(
        targetCamera: CameraPositionState,
        animationDuration: kotlin.time.Duration,
        sampleCount: Int,
        burstLimit: Int,
    ) {
        val contentState = uiState.value as? MapUiState.Content ?: return
        val selectedStyle = contentState.selectedStyle ?: return

        mapTilePrewarmer.prewarm(
            MapTilePrewarmRequest(
                requestKey = CAMERA_PREWARM_REQUEST_KEY,
                style = selectedStyle,
                currentCamera = contentState.cameraPosition,
                targetCamera = targetCamera,
                currentVisibleBounds = _state.value.visibleBounds,
                viewportSize = _state.value.viewportSize,
                animationDuration = animationDuration,
                sampleCount = sampleCount,
                burstLimit = burstLimit,
            ),
        )
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
    private data class UiStateInputs(
        val state: State,
        val fogOfWar: FogOfWarUiState,
        val trackingSession: ExplorationTrackingSession,
        val mapStyleOutput: Output<MapStyle?, DomainError>,
        val pointsOfInterest: List<PointOfInterest>,
    )

    @Immutable
    private data class ResourceDerivedData(
        val queryBounds: GeoBounds?,
        val resourceSpawns: List<ResourceSpawn>,
    )

    @Immutable
    private data class ParsedMapObjectId(
        val kind: MapObjectKind,
        val rawId: String,
    )

    private fun reuseVisibleObjects(visibleObjects: List<MapObjectUiModel>): List<MapObjectUiModel> {
        if (cachedVisibleObjects == visibleObjects) {
            return cachedVisibleObjects
        }

        cachedVisibleObjects = visibleObjects
        return visibleObjects
    }

    private fun resolveResourceQueryBounds(
        visibleBounds: GeoBounds,
        currentQueryBounds: GeoBounds?,
    ): GeoBounds = currentQueryBounds
        ?.takeIf { it.containsInclusive(visibleBounds) }
        ?: visibleBounds.expandedBy(
            horizontalFraction = RESOURCE_QUERY_BUFFER_FRACTION,
            verticalFraction = RESOURCE_QUERY_BUFFER_FRACTION,
        )

    private fun GeoBounds.expandedBy(
        horizontalFraction: Double,
        verticalFraction: Double,
    ): GeoBounds {
        val longitudePadding = (east - west) * horizontalFraction
        val latitudePadding = (north - south) * verticalFraction

        return GeoBounds(
            south = (south - latitudePadding).coerceAtLeast(MIN_LATITUDE),
            west = (west - longitudePadding).coerceAtLeast(MIN_LONGITUDE),
            north = (north + latitudePadding).coerceAtMost(MAX_LATITUDE),
            east = (east + longitudePadding).coerceAtMost(MAX_LONGITUDE),
        )
    }

    private fun GeoBounds.containsInclusive(other: GeoBounds): Boolean {
        return other.south >= south &&
            other.west >= west &&
            other.north <= north &&
            other.east <= east
    }

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
