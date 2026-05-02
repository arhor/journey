package com.github.arhor.journey.feature.map

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.github.arhor.journey.core.common.DomainError
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.core.common.fold
import com.github.arhor.journey.core.common.map
import com.github.arhor.journey.core.common.resolveMessage
import com.github.arhor.journey.core.ui.MviViewModel
import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfig
import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.Watchtower
import com.github.arhor.journey.domain.model.error.ClaimWatchtowerError
import com.github.arhor.journey.domain.model.error.StartExplorationTrackingSessionError
import com.github.arhor.journey.domain.model.error.UpgradeWatchtowerError
import com.github.arhor.journey.domain.usecase.ClaimWatchtowerUseCase
import com.github.arhor.journey.domain.usecase.GetExplorationTileRuntimeConfigUseCase
import com.github.arhor.journey.domain.usecase.GetWatchtowerUseCase
import com.github.arhor.journey.domain.usecase.ObserveCollectibleResourceSpawnsUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.ObserveHeroResourceAmountUseCase
import com.github.arhor.journey.domain.usecase.ObserveSelectedMapStyleUseCase
import com.github.arhor.journey.domain.usecase.ObserveVisibleWatchtowersUseCase
import com.github.arhor.journey.domain.usecase.StartExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.UpgradeWatchtowerUseCase
import com.github.arhor.journey.feature.map.fow.FogOfWarController
import com.github.arhor.journey.feature.map.fow.model.FogOfWarUiState
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.MapObjectKind
import com.github.arhor.journey.feature.map.model.MapObjectUiModel
import com.github.arhor.journey.feature.map.model.MapViewportSize
import com.github.arhor.journey.feature.map.presentation.MapWorldObjectPresenter
import com.github.arhor.journey.feature.map.presentation.SelectedWatchtowerPresenter
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
import com.github.arhor.journey.core.common.combine as combineOutputs

@Immutable
private data class State(
    val cameraPosition: CameraPositionState? = null,
    val cameraUpdateOrigin: CameraUpdateOrigin = CameraUpdateOrigin.PROGRAMMATIC,
    val isUserInteractingCamera: Boolean = false,
    val northResetRequestToken: Int = 0,
    val isAwaitingLocationPermissionResult: Boolean = false,
    val visibleBounds: GeoBounds? = null,
    val viewportSize: MapViewportSize? = null,
    val resourceQueryWindow: GeoBounds? = null,
    val watchtowerMarkerQueryWindow: GeoBounds? = null,
    val selectedWatchtowerId: String? = null,
    val selectedWatchtowerSnapshot: Watchtower? = null,
    val failureMessage: String? = null,
    val startupGate: MapStartupGateState = MapStartupGateState(),
)

@Immutable
private data class MapStartupGateState(
    val sessionId: Long? = null,
    val isLocationReady: Boolean = false,
    val isFirstFrameRendered: Boolean = false,
    val hasTimedOut: Boolean = false,
) {
    val isSplashVisible: Boolean
        get() = sessionId != null && !hasTimedOut && !(isLocationReady && isFirstFrameRendered)
}

@Stable
@HiltViewModel
@Suppress("CanBeParameter")
class MapViewModel @Inject constructor(
    private val observeCollectibleResourceSpawns: ObserveCollectibleResourceSpawnsUseCase,
    private val observeVisibleWatchtowers: ObserveVisibleWatchtowersUseCase,
    private val observeHeroResourceAmount: ObserveHeroResourceAmountUseCase,
    private val claimWatchtower: ClaimWatchtowerUseCase,
    private val upgradeWatchtower: UpgradeWatchtowerUseCase,
    private val getWatchtower: GetWatchtowerUseCase,
    private val getExplorationTileRuntimeConfig: GetExplorationTileRuntimeConfigUseCase,
    private val observeSelectedMapStyle: ObserveSelectedMapStyleUseCase,
    private val fogOfWarControllerFactory: FogOfWarController.Factory,
    private val observeExplorationTrackingSession: ObserveExplorationTrackingSessionUseCase,
    private val startExplorationTrackingSession: StartExplorationTrackingSessionUseCase,
    private val mapObjectQueryWindowPolicy: MapObjectQueryWindowPolicy,
    private val mapWorldObjectPresenter: MapWorldObjectPresenter,
    private val selectedWatchtowerPresenter: SelectedWatchtowerPresenter,
) : MviViewModel<MapUiState, MapEffect, MapIntent>(
    initialState = MapUiState.Loading,
) {
    private val fogOfWarController: FogOfWarController by lazy(LazyThreadSafetyMode.NONE) {
        fogOfWarControllerFactory.create(viewModelScope)
    }

    private val initialTileRuntimeConfig = getExplorationTileRuntimeConfig().fold(
        onSuccess = { it },
        onFailure = { ExplorationTileRuntimeConfig() },
    )
    private val _state = MutableStateFlow(State())
    private val trackingSession = observeExplorationTrackingSession()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = Output.Success(ExplorationTrackingSession()),
        )
    private val watchtowerResourceAmounts = observeWatchtowerResourceAmounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = Output.Success(
                mapOf(
                    ResourceType.SCRAP.typeId to 0,
                    ResourceType.COMPONENTS.typeId to 0,
                    ResourceType.FUEL.typeId to 0,
                ),
            ),
        )
    private val visibleWatchtowerData = observeVisibleWatchtowerData()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = Output.Success(VisibleWatchtowerData()),
        )
    private val selectedMapStyle = observeSelectedMapStyle()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = Output.Success(MapStyle.defaultStyle),
        )

    private var cachedVisibleObjects: List<MapObjectUiModel> = emptyList()

    override fun buildUiState(): Flow<MapUiState> =
        observeBaseUiState()
            .map { it.toUiState() }
            .distinctUntilChanged()

    private fun observeBaseUiState(): Flow<MapBaseUiState> =
        combine(
            combine(
                _state,
                fogOfWarController.uiState,
                trackingSession,
            ) { state, fogOfWar, session ->
                UiStateInputs(
                    state = state,
                    fogOfWar = fogOfWar,
                    trackingSessionOutput = session,
                )
            },
            watchtowerResourceAmounts,
            combine(
                observeVisibleWorldObjects(),
                selectedMapStyle,
            ) { visibleWorldObjects, selectedMapStyle ->
                UiStateStyleInputs(
                    visibleWorldObjectsOutput = visibleWorldObjects,
                    selectedMapStyleOutput = selectedMapStyle,
                )
            },
        ) { inputs, watchtowerResourceAmounts, styleInputs ->
            intoBaseUiState(
                state = inputs.state,
                trackingSessionOutput = inputs.trackingSessionOutput,
                fogOfWar = inputs.fogOfWar,
                visibleWorldObjectsOutput = styleInputs.visibleWorldObjectsOutput,
                watchtowerResourceAmountsOutput = watchtowerResourceAmounts,
                selectedMapStyleOutput = styleInputs.selectedMapStyleOutput,
            )
        }
            .catch {
                emit(
                    MapBaseUiState.Failure(
                        errorMessage = it.message ?: MAP_LOADING_FAILED_MESSAGE,
                    ),
                )
            }
            .distinctUntilChanged()

    private fun observeVisibleWorldObjects(): Flow<Output<VisibleWorldObjects, DomainError>> =
        combine(
            observeVisibleResourceSpawnObjects(),
            visibleWatchtowerData,
        ) { resourceSpawnObjects, watchtowerData ->
            combineOutputs(resourceSpawnObjects, watchtowerData) { resourceSpawnObjectsValue, watchtowerDataValue ->
                VisibleWorldObjects(
                    objects = resourceSpawnObjectsValue + watchtowerDataValue.objects,
                    watchtowers = watchtowerDataValue.watchtowers,
                )
            }
        }
            .distinctUntilChanged()

    private fun observeVisibleResourceSpawnObjects(): Flow<Output<List<MapObjectUiModel>, DomainError>> =
        combine(
            observeResourceDerivedData(),
            observeFogVisibilitySnapshot(),
        ) { resourceDerivedDataOutput, fogVisibility ->
            resourceDerivedDataOutput.map { resourceDerivedData ->
                val canonicalZoom = fogVisibility.canonicalZoom
                    .takeIf { it > 0 }
                    ?: initialTileRuntimeConfig.canonicalZoom

                mapWorldObjectPresenter.presentResourceSpawns(
                    resourceSpawns = resourceDerivedData.resourceSpawns,
                    canonicalZoom = canonicalZoom,
                    visibilityTileMask = fogVisibility.visibilityTileMask,
                )
            }
        }
            .distinctUntilChanged()

    private fun observeVisibleWatchtowerData(): Flow<Output<VisibleWatchtowerData, DomainError>> =
        combine(
            _state.map { it.watchtowerMarkerQueryWindow }.distinctUntilChanged(),
            trackingSession,
            watchtowerResourceAmounts,
        ) { queryWindow, trackingSessionOutput, resourceAmountsOutput ->
            WatchtowerMarkerQueryInputs(
                queryWindow = queryWindow,
                trackingSessionOutput = trackingSessionOutput,
                resourceAmountsOutput = resourceAmountsOutput,
            )
        }
            .flatMapLatest { inputs ->
                inputs.queryWindow
                    ?.let { bounds ->
                        val interactionContext = when (
                            val result = combineOutputs(
                                inputs.trackingSessionOutput,
                                inputs.resourceAmountsOutput,
                            )
                        ) {
                            is Output.Success -> result.value
                            is Output.Failure -> return@flatMapLatest flowOf(Output.Failure(result.error))
                        }

                        observeVisibleWatchtowers(bounds).map { watchtowersOutput ->
                            watchtowersOutput.map { watchtowers ->
                                val trackingSessionValue = interactionContext.first
                                val resourceAmounts = interactionContext.second
                                val decoratedWatchtowers = watchtowers.map { watchtower ->
                                    selectedWatchtowerPresenter.withInteractionContext(
                                        watchtower = watchtower,
                                        actorLocation = trackingSessionValue.lastKnownLocation,
                                        resourceAmounts = resourceAmounts,
                                    )
                                }

                                VisibleWatchtowerData(
                                    watchtowers = decoratedWatchtowers,
                                    objects = decoratedWatchtowers.map(mapWorldObjectPresenter::presentWatchtower),
                                )
                            }
                        }
                    }
                    ?: flowOf(Output.Success(VisibleWatchtowerData()))
            }
            .distinctUntilChanged()

    private fun observeFogVisibilitySnapshot(): Flow<FogVisibilitySnapshot> =
        fogOfWarController.visibilityState
            .map { visibilityState ->
                FogVisibilitySnapshot(
                    canonicalZoom = visibilityState.canonicalZoom,
                    visibilityTileMask = visibilityState.visibilityTileMask,
                )
            }
            .distinctUntilChanged()

    private fun observeResourceDerivedData(): Flow<Output<ResourceDerivedData, DomainError>> =
        _state
            .map { state -> state.resourceQueryWindow }
            .distinctUntilChanged()
            .flatMapLatest { queryBounds ->
                observeVisibleResourceSpawns(queryBounds)
                    .map { resourceSpawnsOutput ->
                        resourceSpawnsOutput.map { resourceSpawns ->
                            ResourceDerivedData(
                                queryWindow = queryBounds,
                                resourceSpawns = resourceSpawns,
                            )
                        }
                    }
            }

    private fun observeWatchtowerResourceAmounts(): Flow<Output<Map<String, Int>, DomainError>> =
        combine(
            observeHeroResourceAmount(ResourceType.SCRAP.typeId),
            observeHeroResourceAmount(ResourceType.COMPONENTS.typeId),
            observeHeroResourceAmount(ResourceType.FUEL.typeId),
        ) { scrapOutput, componentsOutput, fuelOutput ->
            combineOutputs(
                combineOutputs(scrapOutput, componentsOutput),
                fuelOutput,
            ) { (scrap, components), fuel ->
                mapOf(
                    ResourceType.SCRAP.typeId to scrap,
                    ResourceType.COMPONENTS.typeId to components,
                    ResourceType.FUEL.typeId to fuel,
                )
            }
        }.distinctUntilChanged()

    override suspend fun handleIntent(intent: MapIntent) {
        when (intent) {
            is MapIntent.MapOpened -> onMapOpened()
            is MapIntent.MapSurfaceSessionStarted -> onMapSurfaceSessionStarted(intent)
            is MapIntent.FirstLocationFixAcquired -> onFirstLocationFixAcquired(intent)
            is MapIntent.FirstMapFrameRendered -> onFirstMapFrameRendered(intent)
            is MapIntent.StartupGateTimeoutElapsed -> onStartupGateTimeoutElapsed(intent)
            is MapIntent.CameraViewportChanged -> onCameraViewportChanged(intent)
            is MapIntent.MapViewportSizeChanged -> onMapViewportSizeChanged(intent)
            is MapIntent.CameraGestureStarted -> onCameraGestureStarted(intent)
            is MapIntent.CameraSettled -> onCameraSettled(intent)
            is MapIntent.CurrentLocationUnavailable -> onCurrentLocationUnavailable()
            is MapIntent.LocationPermissionResult -> onLocationPermissionResult(intent)
            MapIntent.MapTapped -> onMapTapped()
            is MapIntent.RecenterClicked -> onRecenterClicked()
            is MapIntent.ObjectTapped -> onObjectTapped(intent.objectId)
            MapIntent.DismissWatchtowerSheet -> onDismissWatchtowerSheet()
            MapIntent.ClaimSelectedWatchtower -> onClaimSelectedWatchtower()
            MapIntent.UpgradeSelectedWatchtower -> onUpgradeSelectedWatchtower()
            is MapIntent.MapLoadFailed -> onMapLoadFailed(intent)
        }
    }

    private fun observeVisibleResourceSpawns(
        queryBounds: GeoBounds?,
    ): Flow<Output<List<ResourceSpawn>, DomainError>> = queryBounds
        ?.let(observeCollectibleResourceSpawns::invoke)
        ?: flowOf(Output.Success(emptyList()))

    private suspend fun onMapOpened() {
        startTrackingSessionIfNeeded()
    }

    private fun onMapSurfaceSessionStarted(intent: MapIntent.MapSurfaceSessionStarted) {
        _state.update { state ->
            state.copy(
                startupGate = MapStartupGateState(
                    sessionId = intent.sessionId,
                ),
            )
        }
    }

    private fun onFirstLocationFixAcquired(intent: MapIntent.FirstLocationFixAcquired) {
        _state.updateStartupGateForSession(intent.sessionId) { startupGate ->
            startupGate.copy(isLocationReady = true)
        }
    }

    private fun onFirstMapFrameRendered(intent: MapIntent.FirstMapFrameRendered) {
        _state.updateStartupGateForSession(intent.sessionId) { startupGate ->
            startupGate.copy(isFirstFrameRendered = true)
        }
    }

    private fun onStartupGateTimeoutElapsed(intent: MapIntent.StartupGateTimeoutElapsed) {
        _state.updateStartupGateForSession(intent.sessionId) { startupGate ->
            if (startupGate.isLocationReady && startupGate.isFirstFrameRendered) {
                startupGate
            } else {
                startupGate.copy(hasTimedOut = true)
            }
        }
    }

    private suspend fun startTrackingSessionIfNeeded() {
        when (val result = startExplorationTrackingSession()) {
            is Output.Success -> Unit
            is Output.Failure -> when (val error = result.error) {
                StartExplorationTrackingSessionError.PermissionRequired -> {
                    emitEffect(MapEffect.RequestLocationPermission)
                }

                is StartExplorationTrackingSessionError.LaunchFailed -> {
                    emitEffect(
                        MapEffect.ShowMessage(
                            error.message ?: TRACKING_START_FAILED_MESSAGE,
                        ),
                    )
                }
            }
        }
    }

    private fun intoBaseUiState(
        state: State,
        trackingSessionOutput: Output<ExplorationTrackingSession, DomainError>,
        fogOfWar: FogOfWarUiState,
        visibleWorldObjectsOutput: Output<VisibleWorldObjects, DomainError>,
        watchtowerResourceAmountsOutput: Output<Map<String, Int>, DomainError>,
        selectedMapStyleOutput: Output<MapStyle, DomainError>,
    ): MapBaseUiState = if (state.failureMessage == null) {
        val trackingSession = when (trackingSessionOutput) {
            is Output.Success -> trackingSessionOutput.value
            is Output.Failure -> {
                return MapBaseUiState.Failure(
                    errorMessage = trackingSessionOutput.error.resolveMessage(MAP_LOADING_FAILED_MESSAGE),
                )
            }
        }
        val visibleWorldObjects = when (visibleWorldObjectsOutput) {
            is Output.Success -> visibleWorldObjectsOutput.value
            is Output.Failure -> {
                return MapBaseUiState.Failure(
                    errorMessage = visibleWorldObjectsOutput.error.resolveMessage(MAP_LOADING_FAILED_MESSAGE),
                )
            }
        }
        val watchtowerResourceAmounts = when (watchtowerResourceAmountsOutput) {
            is Output.Success -> watchtowerResourceAmountsOutput.value
            is Output.Failure -> {
                return MapBaseUiState.Failure(
                    errorMessage = watchtowerResourceAmountsOutput.error.resolveMessage(MAP_LOADING_FAILED_MESSAGE),
                )
            }
        }
        val selectedMapStyle = when (selectedMapStyleOutput) {
            is Output.Success -> selectedMapStyleOutput.value
            is Output.Failure -> {
                return MapBaseUiState.Failure(
                    errorMessage = selectedMapStyleOutput.error.resolveMessage(MAP_LOADING_FAILED_MESSAGE),
                )
            }
        }

        val resolvedVisibleObjects = reuseVisibleObjects(visibleWorldObjects.objects)
        val selectedWatchtower = state.selectedWatchtowerId
            ?.let { selectedWatchtowerId ->
                (
                    visibleWorldObjects.watchtowers
                        .firstOrNull { it.id == selectedWatchtowerId }
                        ?: state.selectedWatchtowerSnapshot
                            ?.takeIf { it.id == selectedWatchtowerId }
                            ?.let { watchtower ->
                                selectedWatchtowerPresenter.withInteractionContext(
                                    watchtower = watchtower,
                                    actorLocation = trackingSession.lastKnownLocation,
                                    resourceAmounts = watchtowerResourceAmounts,
                                )
                            }
                    )
                    ?.let { watchtower ->
                        selectedWatchtowerPresenter.present(
                            watchtower = watchtower,
                            resourceAmounts = watchtowerResourceAmounts,
                        )
                    }
            }

        MapBaseUiState.Content(
            cameraPosition = state.cameraPosition,
            cameraUpdateOrigin = state.cameraUpdateOrigin,
            isUserInteractingCamera = state.isUserInteractingCamera,
            northResetRequestToken = state.northResetRequestToken,
            isExplorationTrackingActive = trackingSession.isActive,
            explorationTrackingCadence = trackingSession.cadence,
            explorationTrackingStatus = trackingSession.status,
            isStartupSplashVisible = state.startupGate.isSplashVisible,
            startupSplashMessage = R.string.map_view_startup_loading_message,
            mapStyleUri = selectedMapStyle.value,
            visibleObjects = resolvedVisibleObjects,
            selectedWatchtower = selectedWatchtower,
            fogOfWar = fogOfWar,
        )
    } else {
        MapBaseUiState.Failure(errorMessage = state.failureMessage)
    }

    private fun MapBaseUiState.toUiState(): MapUiState = when (this) {
        is MapBaseUiState.Failure -> MapUiState.Failure(errorMessage)
        is MapBaseUiState.Content -> {
            MapUiState.Content(
                northResetRequestToken = northResetRequestToken,
                isExplorationTrackingActive = isExplorationTrackingActive,
                explorationTrackingCadence = explorationTrackingCadence,
                explorationTrackingStatus = explorationTrackingStatus,
                isStartupSplashVisible = isStartupSplashVisible,
                startupSplashMessage = startupSplashMessage,
                mapStyleUri = mapStyleUri,
                visibleObjects = visibleObjects,
                selectedWatchtower = selectedWatchtower,
                fogOfWar = fogOfWar,
            )
        }
    }

    private fun onMapLoadFailed(intent: MapIntent.MapLoadFailed) {
        _state.update {
            it.copy(
                failureMessage = intent.message ?: MAP_STYLE_LOADING_FAILED_MESSAGE,
                startupGate = MapStartupGateState(),
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
                        isUserInteractingCamera = false,
                        northResetRequestToken = it.northResetRequestToken + 1,
                        cameraPosition = it.cameraPosition?.copy(bearing = DEFAULT_CAMERA_BEARING),
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

    private fun onMapTapped() {
        _state.update {
            it.copy(
                selectedWatchtowerId = null,
                selectedWatchtowerSnapshot = null,
            )
        }
    }

    private fun onCameraViewportChanged(intent: MapIntent.CameraViewportChanged) {
        _state.update { state ->
            state.copy(
                visibleBounds = intent.visibleBounds,
                resourceQueryWindow = mapObjectQueryWindowPolicy.resolveQueryWindow(
                    visibleBounds = intent.visibleBounds,
                    currentQueryWindow = state.resourceQueryWindow,
                ),
                watchtowerMarkerQueryWindow = mapObjectQueryWindowPolicy.resolveQueryWindow(
                    visibleBounds = intent.visibleBounds,
                    currentQueryWindow = state.watchtowerMarkerQueryWindow,
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
                state.isUserInteractingCamera
            ) {
                state
            } else {
                state.copy(
                    cameraPosition = intent.position,
                    cameraUpdateOrigin = CameraUpdateOrigin.USER,
                    isUserInteractingCamera = true,
                )
            }
        }
    }

    private fun onCameraSettled(intent: MapIntent.CameraSettled) {
        _state.update { state ->
            state.copy(
                cameraPosition = state.resolveSettledCameraPosition(intent),
                cameraUpdateOrigin = intent.origin,
                isUserInteractingCamera = false,
            )
        }
    }

    private fun State.resolveSettledCameraPosition(intent: MapIntent.CameraSettled): CameraPositionState {
        val isInitialProgrammaticSettle = cameraPosition == null && intent.origin == CameraUpdateOrigin.PROGRAMMATIC

        return if (isInitialProgrammaticSettle && intent.position.zoom < DEFAULT_CAMERA_ZOOM) {
            intent.position.copy(zoom = DEFAULT_CAMERA_ZOOM)
        } else {
            intent.position
        }
    }

    private fun onDismissWatchtowerSheet() {
        _state.update { it.copy(selectedWatchtowerId = null, selectedWatchtowerSnapshot = null) }
    }

    private suspend fun onObjectTapped(objectId: String) {
        val contentState = uiState.value as? MapUiState.Content ?: return
        val objectUiModel = contentState.visibleObjects
            .firstOrNull { it.id == objectId }
            ?: return
        val parsedId = mapWorldObjectPresenter.parseObjectId(objectUiModel.id) ?: return

        val selectedWatchtowerSnapshot = if (parsedId.kind == MapObjectKind.Watchtower) {
            when (val result = getWatchtower(parsedId.rawId)) {
                is Output.Success -> result.value
                is Output.Failure -> {
                    (visibleWatchtowerData.value as? Output.Success)
                        ?.value
                        ?.watchtowers
                        ?.firstOrNull { watchtower ->
                            watchtower.id == parsedId.rawId
                        }
                }
            }
        } else {
            null
        }

        _state.update {
            it.copy(
                selectedWatchtowerId = if (parsedId.kind == MapObjectKind.Watchtower) {
                    parsedId.rawId
                } else {
                    null
                },
                selectedWatchtowerSnapshot = selectedWatchtowerSnapshot,
            )
        }
    }

    private suspend fun onClaimSelectedWatchtower() {
        val selectedWatchtowerId = _state.value.selectedWatchtowerId ?: return
        val actorLocation = currentTrackingSession()?.lastKnownLocation
            ?: return emitEffect(MapEffect.ShowMessage(CURRENT_LOCATION_UNAVAILABLE_MESSAGE))

        when (val result = claimWatchtower(selectedWatchtowerId, actorLocation)) {
            is Output.Success -> {
                refreshSelectedWatchtowerSnapshot(selectedWatchtowerId)
                emitEffect(MapEffect.ShowMessage(WATCHTOWER_CLAIMED_MESSAGE))
            }

            is Output.Failure -> when (val error = result.error) {
                is ClaimWatchtowerError.AlreadyClaimed -> {
                    emitEffect(MapEffect.ShowMessage(WATCHTOWER_ALREADY_CLAIMED_MESSAGE))
                }

                is ClaimWatchtowerError.NotDiscovered -> {
                    emitEffect(MapEffect.ShowMessage(WATCHTOWER_NOT_DISCOVERED_MESSAGE))
                }

                is ClaimWatchtowerError.NotFound -> {
                    _state.update { it.copy(selectedWatchtowerId = null, selectedWatchtowerSnapshot = null) }
                    emitEffect(MapEffect.ShowMessage(WATCHTOWER_NOT_FOUND_MESSAGE))
                }

                is ClaimWatchtowerError.NotInRange -> {
                    emitEffect(MapEffect.ShowMessage(WATCHTOWER_OUT_OF_RANGE_MESSAGE))
                }

                is ClaimWatchtowerError.InsufficientResources -> {
                    emitEffect(
                        MapEffect.ShowMessage(
                            selectedWatchtowerPresenter.resourceRequirementMessage(error.resourceTypeId),
                        ),
                    )
                }

                is ClaimWatchtowerError.Unexpected -> {
                    emitEffect(
                        MapEffect.ShowMessage(
                            error.resolveMessage(WATCHTOWER_CLAIM_FAILED_MESSAGE),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun onUpgradeSelectedWatchtower() {
        val selectedWatchtowerId = _state.value.selectedWatchtowerId ?: return
        val actorLocation = currentTrackingSession()?.lastKnownLocation
            ?: return emitEffect(MapEffect.ShowMessage(CURRENT_LOCATION_UNAVAILABLE_MESSAGE))

        when (val result = upgradeWatchtower(selectedWatchtowerId, actorLocation)) {
            is Output.Success -> {
                refreshSelectedWatchtowerSnapshot(selectedWatchtowerId)
                emitEffect(
                    MapEffect.ShowMessage(
                        WATCHTOWER_UPGRADED_MESSAGE_PREFIX + result.value.level,
                    ),
                )
            }

            is Output.Failure -> when (val error = result.error) {
                is UpgradeWatchtowerError.AlreadyAtMaxLevel -> {
                    emitEffect(MapEffect.ShowMessage(WATCHTOWER_MAX_LEVEL_MESSAGE))
                }

                is UpgradeWatchtowerError.NotClaimed -> {
                    emitEffect(MapEffect.ShowMessage(WATCHTOWER_NOT_CLAIMED_MESSAGE))
                }

                is UpgradeWatchtowerError.NotFound -> {
                    _state.update { it.copy(selectedWatchtowerId = null, selectedWatchtowerSnapshot = null) }
                    emitEffect(MapEffect.ShowMessage(WATCHTOWER_NOT_FOUND_MESSAGE))
                }

                is UpgradeWatchtowerError.NotInRange -> {
                    emitEffect(MapEffect.ShowMessage(WATCHTOWER_OUT_OF_RANGE_MESSAGE))
                }

                is UpgradeWatchtowerError.InsufficientResources -> {
                    emitEffect(
                        MapEffect.ShowMessage(
                            selectedWatchtowerPresenter.resourceRequirementMessage(error.resourceTypeId),
                        ),
                    )
                }

                is UpgradeWatchtowerError.Unexpected -> {
                    emitEffect(
                        MapEffect.ShowMessage(
                            error.resolveMessage(WATCHTOWER_UPGRADE_FAILED_MESSAGE),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun refreshSelectedWatchtowerSnapshot(id: String) {
        val snapshot = when (val result = getWatchtower(id)) {
            is Output.Success -> result.value
            is Output.Failure -> return
        }
        _state.update { state ->
            if (state.selectedWatchtowerId == id) {
                state.copy(selectedWatchtowerSnapshot = snapshot)
            } else {
                state
            }
        }
    }

    private fun currentTrackingSession(): ExplorationTrackingSession? =
        when (val result = trackingSession.value) {
            is Output.Success -> result.value
            is Output.Failure -> null
        }

    private fun MutableStateFlow<State>.updateStartupGateForSession(
        sessionId: Long,
        transform: (MapStartupGateState) -> MapStartupGateState,
    ) {
        update { state ->
            if (state.startupGate.sessionId != sessionId) {
                state
            } else {
                state.copy(startupGate = transform(state.startupGate))
            }
        }
    }

    @Immutable
    private sealed interface MapBaseUiState {

        @Immutable
        data class Failure(
            val errorMessage: String,
        ) : MapBaseUiState

        @Immutable
        data class Content(
            val cameraPosition: CameraPositionState?,
            val cameraUpdateOrigin: CameraUpdateOrigin,
            val isUserInteractingCamera: Boolean,
            val northResetRequestToken: Int,
            val isExplorationTrackingActive: Boolean,
            val explorationTrackingCadence: ExplorationTrackingCadence,
            val explorationTrackingStatus: ExplorationTrackingStatus,
            val isStartupSplashVisible: Boolean,
            val startupSplashMessage: Int,
            val mapStyleUri: String,
            val visibleObjects: List<MapObjectUiModel>,
            val selectedWatchtower: WatchtowerSheetUiState?,
            val fogOfWar: FogOfWarUiState,
        ) : MapBaseUiState
    }

    @Immutable
    private data class UiStateInputs(
        val state: State,
        val fogOfWar: FogOfWarUiState,
        val trackingSessionOutput: Output<ExplorationTrackingSession, DomainError>,
    )

    @Immutable
    private data class UiStateStyleInputs(
        val visibleWorldObjectsOutput: Output<VisibleWorldObjects, DomainError>,
        val selectedMapStyleOutput: Output<MapStyle, DomainError>,
    )

    @Immutable
    private data class ResourceDerivedData(
        val queryWindow: GeoBounds?,
        val resourceSpawns: List<ResourceSpawn>,
    )

    @Immutable
    private data class WatchtowerMarkerQueryInputs(
        val queryWindow: GeoBounds?,
        val trackingSessionOutput: Output<ExplorationTrackingSession, DomainError>,
        val resourceAmountsOutput: Output<Map<String, Int>, DomainError>,
    )

    @Immutable
    private data class VisibleWorldObjects(
        val objects: List<MapObjectUiModel> = emptyList(),
        val watchtowers: List<Watchtower> = emptyList(),
    )

    @Immutable
    private data class VisibleWatchtowerData(
        val watchtowers: List<Watchtower> = emptyList(),
        val objects: List<MapObjectUiModel> = emptyList(),
    )

    @Immutable
    private data class FogVisibilitySnapshot(
        val canonicalZoom: Int,
        val visibilityTileMask: Set<MapTile>,
    )

    private fun reuseVisibleObjects(visibleObjects: List<MapObjectUiModel>): List<MapObjectUiModel> {
        if (cachedVisibleObjects == visibleObjects) {
            return cachedVisibleObjects
        }

        cachedVisibleObjects = visibleObjects
        return visibleObjects
    }

    private companion object {
        const val MAP_LOADING_FAILED_MESSAGE = "Failed to load map state."
        const val MAP_STYLE_LOADING_FAILED_MESSAGE = "Failed to load map style."
        const val TRACKING_PERMISSION_REQUIRED_MESSAGE =
            "Location permission is required to start exploration tracking."
        const val LOCATION_PERMISSION_DENIED_MESSAGE =
            "Location permission is required to center the map on your position."
        const val CURRENT_LOCATION_UNAVAILABLE_MESSAGE =
            "Current location is not available yet."
        const val TRACKING_START_FAILED_MESSAGE =
            "Failed to start exploration tracking."
        const val WATCHTOWER_CLAIMED_MESSAGE = "Watchtower claimed."
        const val WATCHTOWER_CLAIM_FAILED_MESSAGE = "Failed to claim watchtower."
        const val WATCHTOWER_ALREADY_CLAIMED_MESSAGE = "Watchtower is already claimed."
        const val WATCHTOWER_NOT_DISCOVERED_MESSAGE = "Discover the watchtower before claiming it."
        const val WATCHTOWER_NOT_FOUND_MESSAGE = "Watchtower is no longer available."
        const val WATCHTOWER_OUT_OF_RANGE_MESSAGE = "Move closer to interact with the watchtower."
        const val WATCHTOWER_NOT_CLAIMED_MESSAGE = "Claim the watchtower before upgrading it."
        const val WATCHTOWER_MAX_LEVEL_MESSAGE = "Watchtower is already at maximum level."
        const val WATCHTOWER_UPGRADED_MESSAGE_PREFIX = "Watchtower upgraded to level "
        const val WATCHTOWER_UPGRADE_FAILED_MESSAGE = "Failed to upgrade watchtower."
    }
}
