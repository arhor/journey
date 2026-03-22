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
import com.github.arhor.journey.feature.map.model.FogOfWarDiagnostics
import com.github.arhor.journey.feature.map.model.FogOfWarPreparationMetrics
import com.github.arhor.journey.feature.map.model.FogOfWarRenderData
import com.github.arhor.journey.feature.map.model.FogOfWarSourceUpdateMetrics
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapObjectKind
import com.github.arhor.journey.feature.map.model.MapObjectUiModel
import com.github.arhor.journey.feature.map.renderer.FogOfWarRenderDataFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource
import javax.inject.Inject

private const val DEFAULT_CAMERA_ZOOM = 17.0
private const val MAX_VISIBLE_FOG_TILE_COUNT = 8_192L
private const val MAX_BUFFERED_FOG_TILE_COUNT = MAX_VISIBLE_FOG_TILE_COUNT * 9
private const val RESOURCE_QUERY_BUFFER_FRACTION = 0.5
private const val MIN_LONGITUDE = -180.0
private const val MAX_LONGITUDE = 180.0 - 1e-9
private const val MIN_LATITUDE = -85.05112878
private const val MAX_LATITUDE = 85.05112878

@Immutable
private data class DisplayedFogData(
    val exploredTiles: Set<ExplorationTile>,
    val fogRanges: List<ExplorationTileRange>,
    val renderData: FogOfWarRenderData?,
    val preparationMetrics: FogOfWarPreparationMetrics,
)

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
    val resourceQueryBounds: GeoBounds? = null,
    val visibleTileRange: ExplorationTileRange? = null,
    val displayedFogBuffer: FogBufferRegion? = null,
    val pendingFogBuffer: FogBufferRegion? = null,
    val displayedFogData: DisplayedFogData? = null,
    val visibleTileCount: Long = 0,
    val isFogSuppressedByVisibleTileLimit: Boolean = false,
    val isFogRecomputationInProgress: Boolean = false,
    val fogSourceUpdateMetrics: FogOfWarSourceUpdateMetrics = FogOfWarSourceUpdateMetrics(),
    val fogPreparationCancellationCount: Long = 0,
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
    private var displayedFogObservationJob: Job? = null
    private var pendingFogPreparationJob: Job? = null

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
            observeResourceDerivedData(),
        ) { inputs, resourceDerivedData ->
            intoUiState(
                state = inputs.state,
                trackingSession = inputs.trackingSession,
                mapStyleOutput = inputs.mapStyleOutput,
                pointsOfInterest = inputs.pointsOfInterest,
                explorationProgress = inputs.explorationProgress,
                fogOfWar = fogOfWarUiState(state = inputs.state),
                resourceSpawns = resourceDerivedData.takeIf {
                    it.queryBounds == inputs.state.resourceQueryBounds
                }?.resourceSpawns ?: emptyList(),
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
            is MapIntent.FogOfWarSourceUpdated -> onFogOfWarSourceUpdated(intent)
        }
    }

    private fun observeFogExploredTiles(
        fogTileRange: ExplorationTileRange?,
    ): Flow<Set<ExplorationTile>> = fogTileRange
        ?.let(observeExploredTiles::invoke)
        ?: flowOf(emptySet())

    private fun observeVisibleResourceSpawns(
        queryBounds: GeoBounds?,
    ): Flow<List<ResourceSpawn>> = queryBounds
        ?.let(observeCollectibleResourceSpawns::invoke)
        ?: flowOf(emptyList())

    private suspend fun prepareFogBufferData(
        buffer: FogBufferRegion,
        exploredTiles: Set<ExplorationTile>,
        visibleTileCount: Long,
    ): PreparedFogBuffer {
        // Fog geometry is CPU-bound; keep it off the main dispatcher.
        return withContext(Dispatchers.Default) {
            val coroutineContext = currentCoroutineContext()
            val checkCancelled = { coroutineContext.ensureActive() }
            val diagnosticsEnabled = BuildConfig.DEBUG
            val totalStartedAt = if (diagnosticsEnabled) TimeSource.Monotonic.markNow() else null
            val calculateFogRangesStartedAt = if (diagnosticsEnabled) TimeSource.Monotonic.markNow() else null
            val fogRanges = calculateUnexploredFogRanges(
                tileRange = buffer.bufferedTileRange,
                exploredTiles = exploredTiles,
                checkCancelled = checkCancelled,
            )
            val calculateFogRangesMillis = calculateFogRangesStartedAt?.elapsedNow()?.inWholeMilliseconds ?: 0
            val buildRenderDataStartedAt = if (diagnosticsEnabled) TimeSource.Monotonic.markNow() else null
            val renderOutput = if (diagnosticsEnabled) {
                fogOfWarRenderDataFactory.createDetailed(
                    fogRanges = fogRanges,
                    checkCancelled = checkCancelled,
                )
            } else {
                null
            }
            val renderData = renderOutput?.renderData ?: fogOfWarRenderDataFactory.create(
                fogRanges = fogRanges,
                checkCancelled = checkCancelled,
            )
            val buildRenderDataMillis = buildRenderDataStartedAt?.elapsedNow()?.inWholeMilliseconds ?: 0

            PreparedFogBuffer(
                buffer = buffer,
                data = DisplayedFogData(
                    exploredTiles = exploredTiles,
                    fogRanges = fogRanges,
                    renderData = renderData,
                    preparationMetrics = FogOfWarPreparationMetrics(
                        totalPrepareMillis = totalStartedAt?.elapsedNow()?.inWholeMilliseconds ?: 0,
                        calculateFogRangesMillis = calculateFogRangesMillis,
                        buildRenderDataMillis = buildRenderDataMillis,
                        geometryBuildMillis = renderOutput?.metrics?.geometryBuildMillis ?: 0,
                        featureCollectionBuildMillis = renderOutput?.metrics?.featureCollectionBuildMillis ?: 0,
                        visibleTileCount = visibleTileCount,
                        bufferedTileCount = buffer.bufferedTileRange.tileCount,
                        exploredTileCount = exploredTiles.size,
                        fogRangeCount = fogRanges.size,
                        expandedFogCellCount = renderOutput?.metrics?.expandedFogCellCount ?: 0,
                        connectedRegionCount = renderOutput?.metrics?.connectedRegionCount ?: 0,
                        boundaryEdgeCount = renderOutput?.metrics?.boundaryEdgeCount ?: 0,
                        loopCount = renderOutput?.metrics?.loopCount ?: 0,
                        featureCount = renderOutput?.metrics?.featureCount ?: 0,
                        ringPointCount = renderOutput?.metrics?.ringPointCount ?: 0,
                        renderCacheHit = renderOutput?.metrics?.cacheHit ?: false,
                    ),
                ),
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
        val visibleBounds = _state.value.visibleBounds

        _state.update { state ->
            state.copy(canonicalZoom = canonicalZoom)
        }

        visibleBounds?.let { updateFogViewport(it, forceDisplayedBufferReplacement = true) }
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

    private fun fogOfWarUiState(state: State): FogOfWarUiState {
        val displayedFogData = state.displayedFogData
        val exploredVisibleTileCount = state.visibleTileRange?.let { visibleRange ->
            displayedFogData?.exploredTiles?.count(visibleRange::contains)
        } ?: 0
        val fallbackFogTileRange = state.displayedFogBuffer
            ?.bufferedTileRange
            .takeUnless { state.isFogSuppressedByVisibleTileLimit }
        val fogRanges = displayedFogData?.fogRanges
            ?: fallbackFogTileRange?.let(::listOf)
            ?: emptyList()
        val renderData = displayedFogData?.renderData
            ?: fallbackFogTileRange
                ?.takeIf { state.isFogOfWarOverlayEnabled }
                ?.let(fogOfWarRenderDataFactory::createFullRange)
        val preparationMetrics = displayedFogData?.preparationMetrics ?: fallbackPreparationMetrics(
            state = state,
            fogRanges = fogRanges,
            hasRenderData = renderData != null,
        )

        return FogOfWarUiState(
            canonicalZoom = state.canonicalZoom,
            visibleBounds = state.visibleBounds,
            triggerBounds = state.displayedFogBuffer?.triggerBounds,
            bufferedBounds = state.displayedFogBuffer?.bufferedBounds,
            visibleTileRange = state.visibleTileRange,
            fogRanges = fogRanges,
            renderData = renderData,
            visibleTileCount = state.visibleTileCount,
            exploredVisibleTileCount = exploredVisibleTileCount,
            isSuppressedByVisibleTileLimit = state.isFogSuppressedByVisibleTileLimit,
            isRecomputing = state.isFogRecomputationInProgress,
            diagnostics = if (BuildConfig.DEBUG) {
                FogOfWarDiagnostics(
                    lastPreparation = preparationMetrics,
                    cache = fogOfWarRenderDataFactory.cacheMetricsSnapshot(),
                    sourceUpdate = state.fogSourceUpdateMetrics,
                    prepareCancellationCount = state.fogPreparationCancellationCount,
                )
            } else {
                FogOfWarDiagnostics(lastPreparation = preparationMetrics)
            },
        )
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

    private fun onFogOfWarSourceUpdated(intent: MapIntent.FogOfWarSourceUpdated) {
        _state.update { state ->
            state.copy(
                fogSourceUpdateMetrics = state.fogSourceUpdateMetrics.copy(
                    updateCount = state.fogSourceUpdateMetrics.updateCount + 1,
                    lastSetDataMillis = intent.elapsedMillis,
                ),
            )
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

    private fun updateFogViewport(
        visibleBounds: GeoBounds,
        forceDisplayedBufferReplacement: Boolean = false,
    ) {
        val currentState = _state.value
        val viewport = createFogViewportSnapshot(
            visibleBounds = visibleBounds,
            canonicalZoom = currentState.canonicalZoom,
        )
        val nextFogBuffer = createFogBufferRegion(viewport.visibleTileRange)
        val resourceQueryBounds = resolveResourceQueryBounds(
            visibleBounds = visibleBounds,
            currentQueryBounds = currentState.resourceQueryBounds,
        )
        val isSuppressedByVisibleTileLimit = viewport.visibleTileCount > MAX_VISIBLE_FOG_TILE_COUNT
        val isSuppressedByBufferedTileLimit = nextFogBuffer.bufferedTileRange.tileCount > MAX_BUFFERED_FOG_TILE_COUNT
        val isFogSuppressed = isSuppressedByVisibleTileLimit || isSuppressedByBufferedTileLimit

        _state.update {
            it.copy(
                visibleBounds = visibleBounds,
                resourceQueryBounds = resourceQueryBounds,
                visibleTileRange = viewport.visibleTileRange,
                visibleTileCount = viewport.visibleTileCount,
                isFogSuppressedByVisibleTileLimit = isFogSuppressed,
            )
        }

        if (isFogSuppressed) {
            clearFogBufferState()
            return
        }

        val updatedState = _state.value
        val displayedFogBuffer = updatedState.displayedFogBuffer
            ?.takeIf { it.bufferedTileRange.zoom == updatedState.canonicalZoom }
        val pendingFogBuffer = updatedState.pendingFogBuffer
            ?.takeIf { it.bufferedTileRange.zoom == updatedState.canonicalZoom }

        when {
            forceDisplayedBufferReplacement || displayedFogBuffer == null -> {
                activateDisplayedFogBuffer(nextFogBuffer)
            }

            !displayedFogBuffer.shouldRecompute(visibleBounds) -> Unit

            pendingFogBuffer?.shouldRecompute(visibleBounds) == false -> Unit

            else -> {
                preparePendingFogBuffer(nextFogBuffer)
            }
        }
    }

    private fun clearFogBufferState() {
        displayedFogObservationJob?.cancel()
        displayedFogObservationJob = null
        pendingFogPreparationJob?.cancel()
        pendingFogPreparationJob = null

        _state.update {
            it.copy(
                displayedFogBuffer = null,
                pendingFogBuffer = null,
                displayedFogData = null,
                isFogRecomputationInProgress = false,
            )
        }
    }

    private fun activateDisplayedFogBuffer(buffer: FogBufferRegion) {
        pendingFogPreparationJob?.cancel()
        pendingFogPreparationJob = null

        _state.update {
            it.copy(
                displayedFogBuffer = buffer,
                pendingFogBuffer = null,
                displayedFogData = null,
                isFogRecomputationInProgress = false,
            )
        }

        startObservingDisplayedFogBuffer(buffer)
    }

    private fun preparePendingFogBuffer(buffer: FogBufferRegion) {
        if (_state.value.pendingFogBuffer == buffer && pendingFogPreparationJob?.isActive == true) {
            return
        }

        pendingFogPreparationJob?.cancel()
        _state.update {
            it.copy(
                pendingFogBuffer = buffer,
                isFogRecomputationInProgress = true,
            )
        }

        pendingFogPreparationJob = viewModelScope.launch {
            val exploredTiles = observeFogExploredTiles(buffer.bufferedTileRange).first()
            val preparedFogBuffer = try {
                prepareFogBufferData(
                    buffer = buffer,
                    exploredTiles = exploredTiles,
                    visibleTileCount = _state.value.visibleTileCount,
                )
            } catch (exception: CancellationException) {
                recordFogPreparationCancellation()
                throw exception
            }
            var didSwap = false
            var shouldPrepareAnotherBuffer = false

            // Keep the old fog source active until the replacement render payload is fully ready.
            _state.update { current ->
                if (current.pendingFogBuffer != buffer || current.isFogSuppressedByVisibleTileLimit) {
                    current
                } else {
                    didSwap = true
                    val updatedState = current.copy(
                        displayedFogBuffer = buffer,
                        pendingFogBuffer = null,
                        displayedFogData = preparedFogBuffer.data,
                        isFogRecomputationInProgress = false,
                    )
                    shouldPrepareAnotherBuffer = updatedState.visibleBounds?.let(buffer::shouldRecompute) == true
                    updatedState
                }
            }

            if (!didSwap) {
                return@launch
            }

            pendingFogPreparationJob = null
            startObservingDisplayedFogBuffer(
                buffer = buffer,
                seedExploredTiles = preparedFogBuffer.data.exploredTiles,
            )

            if (shouldPrepareAnotherBuffer) {
                _state.value.visibleBounds?.let(::updateFogViewport)
            }
        }
    }

    private fun startObservingDisplayedFogBuffer(
        buffer: FogBufferRegion,
        seedExploredTiles: Set<ExplorationTile>? = null,
    ) {
        displayedFogObservationJob?.cancel()
        displayedFogObservationJob = viewModelScope.launch {
            var shouldSkipSeed = seedExploredTiles != null

            observeFogExploredTiles(buffer.bufferedTileRange)
                .distinctUntilChanged()
                .collectLatest { exploredTiles ->
                    if (shouldSkipSeed && exploredTiles == seedExploredTiles) {
                        shouldSkipSeed = false
                        return@collectLatest
                    }

                    shouldSkipSeed = false
                    val preparedFogBuffer = try {
                        prepareFogBufferData(
                            buffer = buffer,
                            exploredTiles = exploredTiles,
                            visibleTileCount = _state.value.visibleTileCount,
                        )
                    } catch (exception: CancellationException) {
                        recordFogPreparationCancellation()
                        throw exception
                    }

                    _state.update { current ->
                        if (current.displayedFogBuffer != buffer || current.isFogSuppressedByVisibleTileLimit) {
                            current
                        } else {
                            current.copy(displayedFogData = preparedFogBuffer.data)
                        }
                    }
                }
        }
    }

    private fun recordFogPreparationCancellation() {
        _state.update { state ->
            state.copy(
                fogPreparationCancellationCount = state.fogPreparationCancellationCount + 1,
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
    private data class UiStateInputs(
        val state: State,
        val trackingSession: ExplorationTrackingSession,
        val mapStyleOutput: Output<MapStyle?, DomainError>,
        val pointsOfInterest: List<PointOfInterest>,
        val explorationProgress: ExplorationProgress,
    )

    @Immutable
    private data class PreparedFogBuffer(
        val buffer: FogBufferRegion,
        val data: DisplayedFogData,
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

    private fun resolveResourceQueryBounds(
        visibleBounds: GeoBounds,
        currentQueryBounds: GeoBounds?,
    ): GeoBounds = currentQueryBounds
        ?.takeIf { it.containsInclusive(visibleBounds) }
        ?: visibleBounds.expandedBy(
            horizontalFraction = RESOURCE_QUERY_BUFFER_FRACTION,
            verticalFraction = RESOURCE_QUERY_BUFFER_FRACTION,
        )

    private fun fallbackPreparationMetrics(
        state: State,
        fogRanges: List<ExplorationTileRange>,
        hasRenderData: Boolean,
    ): FogOfWarPreparationMetrics = FogOfWarPreparationMetrics(
        visibleTileCount = state.visibleTileCount,
        bufferedTileCount = state.displayedFogBuffer?.bufferedTileRange?.tileCount ?: 0,
        fogRangeCount = fogRanges.size,
        featureCount = if (hasRenderData) 1 else 0,
        ringPointCount = if (hasRenderData) 5 else 0,
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
