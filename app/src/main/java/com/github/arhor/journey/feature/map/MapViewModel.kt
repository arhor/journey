package com.github.arhor.journey.feature.map

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.github.arhor.journey.R
import com.github.arhor.journey.core.common.DomainError
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.common.map as mapOutput
import com.github.arhor.journey.core.common.resolveMessage
import com.github.arhor.journey.core.ui.MviViewModel
import com.github.arhor.journey.domain.model.BreachNode
import com.github.arhor.journey.domain.model.BreachNodePhase
import com.github.arhor.journey.domain.model.BreachNodeRecord
import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.error.StartExplorationTrackingSessionError
import com.github.arhor.journey.domain.usecase.CompleteBreachUseCase
import com.github.arhor.journey.domain.usecase.DiscoverBreachNodeUseCase
import com.github.arhor.journey.domain.usecase.FindNearestBreachNodeUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.ObserveControlledBreachRevealCellsUseCase
import com.github.arhor.journey.domain.usecase.ObserveSelectedMapStyleUseCase
import com.github.arhor.journey.domain.usecase.ObserveVisibleBreachNodesUseCase
import com.github.arhor.journey.domain.usecase.StartExplorationTrackingSessionUseCase
import com.github.arhor.journey.feature.map.fow.FogOfWarController
import com.github.arhor.journey.feature.map.fow.model.FogOfWarUiState
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.MapObjectUiModel
import com.github.arhor.journey.feature.map.model.MapViewportSize
import com.github.arhor.journey.feature.map.presentation.BreachNodePresenter
import com.github.arhor.journey.feature.map.presentation.BreachDirectionalGuidancePresenter
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

@Immutable
private data class State(
    val cameraPosition: CameraPositionState? = null,
    val cameraUpdateOrigin: CameraUpdateOrigin = CameraUpdateOrigin.PROGRAMMATIC,
    val isUserInteractingCamera: Boolean = false,
    val northResetRequestToken: Int = 0,
    val isAwaitingLocationPermissionResult: Boolean = false,
    val viewportSize: MapViewportSize? = null,
    val visibleBounds: GeoBounds? = null,
    val failureMessage: String? = null,
    val breachProtocol: BreachProtocolUiState = BreachProtocolUiState.Idle,
    val lockedBreach: BreachNode? = null,
    val uploadProgressPercent: Int = 0,
    val breachPhase: BreachSessionPhase = BreachSessionPhase.IDLE,
    val startupGate: MapStartupGateState = MapStartupGateState(),
)

private enum class BreachSessionPhase {
    IDLE,
    SCANNING,
    SIGNAL_LOCKED,
    UPLOADING,
    COMPLETED,
}

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
    private val observeSelectedMapStyle: ObserveSelectedMapStyleUseCase,
    private val fogOfWarControllerFactory: FogOfWarController.Factory,
    private val observeExplorationTrackingSession: ObserveExplorationTrackingSessionUseCase,
    private val startExplorationTrackingSession: StartExplorationTrackingSessionUseCase,
    private val findNearestBreachNode: FindNearestBreachNodeUseCase,
    private val discoverBreachNode: DiscoverBreachNodeUseCase,
    private val completeBreach: CompleteBreachUseCase,
    private val observeVisibleBreachNodes: ObserveVisibleBreachNodesUseCase,
    private val observeControlledBreachRevealCells: ObserveControlledBreachRevealCellsUseCase,
    private val breachNodePresenter: BreachNodePresenter,
    private val breachDirectionalGuidancePresenter: BreachDirectionalGuidancePresenter,
) : MviViewModel<MapUiState, MapEffect, MapIntent>(
    initialState = MapUiState.Loading,
) {
    private val fogOfWarController: FogOfWarController by lazy(LazyThreadSafetyMode.NONE) {
        fogOfWarControllerFactory.create(viewModelScope)
    }

    private val _state = MutableStateFlow(State())
    private val trackingSession = observeExplorationTrackingSession()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = Output.Success(ExplorationTrackingSession()),
        )
    private val selectedMapStyle = observeSelectedMapStyle()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = Output.Success(MapStyle.defaultStyle),
        )
    private val visibleBreachObjects: Flow<Output<List<MapObjectUiModel>, DomainError>> = _state
        .map { state -> state.visibleBounds }
        .distinctUntilChanged()
        .flatMapLatest { bounds ->
            if (bounds == null) {
                flowOf<Output<List<MapObjectUiModel>, DomainError>>(Output.Success(emptyList()))
            } else {
                observeVisibleBreachNodes(bounds).map { output ->
                    output.mapOutput { visibleBreaches ->
                        visibleBreaches.map(breachNodePresenter::present)
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = Output.Success(emptyList()),
        )

    override fun buildUiState(): Flow<MapUiState> =
        observeBaseUiState()
            .map { it.toUiState() }
            .distinctUntilChanged()

    private fun observeBaseUiState(): Flow<MapBaseUiState> =
        combine(
            _state,
            fogOfWarController.uiState,
            trackingSession,
            selectedMapStyle,
            visibleBreachObjects,
        ) { state, fogOfWar, trackingSessionOutput, selectedMapStyleOutput, visibleBreachObjectsOutput ->
            intoBaseUiState(
                state = state,
                trackingSessionOutput = trackingSessionOutput,
                fogOfWar = fogOfWar,
                selectedMapStyleOutput = selectedMapStyleOutput,
                visibleBreachObjectsOutput = visibleBreachObjectsOutput,
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
            MapIntent.PulseClicked -> onPulseClicked()
            MapIntent.StartBreachUpload -> onStartBreachUpload()
            MapIntent.BreachUploadTick -> onBreachUploadTick()
            MapIntent.CancelBreachUpload -> onCancelBreachUpload()
            MapIntent.DismissBreachPanel -> onDismissBreachPanel()
            is MapIntent.MapLoadFailed -> onMapLoadFailed(intent)
        }
    }

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
        trackingSessionOutput: Output<ExplorationTrackingSession, *>,
        fogOfWar: FogOfWarUiState,
        selectedMapStyleOutput: Output<MapStyle, *>,
        visibleBreachObjectsOutput: Output<List<MapObjectUiModel>, *>,
    ): MapBaseUiState = if (state.failureMessage == null) {
        val trackingSessionValue = when (trackingSessionOutput) {
            is Output.Success -> trackingSessionOutput.value
            is Output.Failure -> {
                return MapBaseUiState.Failure(
                    errorMessage = trackingSessionOutput.error.resolveMessage(MAP_LOADING_FAILED_MESSAGE),
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

        val mapMode = state.toMapMode(trackingSessionValue)

        MapBaseUiState.Content(
            cameraPosition = state.cameraPosition,
            cameraUpdateOrigin = state.cameraUpdateOrigin,
            isUserInteractingCamera = state.isUserInteractingCamera,
            northResetRequestToken = state.northResetRequestToken,
            isExplorationTrackingActive = trackingSessionValue.isActive,
            explorationTrackingCadence = trackingSessionValue.cadence,
            explorationTrackingStatus = trackingSessionValue.status,
            breachProtocol = state.resolveBreachProtocolUiState(trackingSessionValue),
            breachGuidance = state.resolveBreachGuidanceUiState(trackingSessionValue),
            isStartupSplashVisible = state.startupGate.isSplashVisible,
            startupSplashMessage = R.string.map_view_startup_loading_message,
            mapMode = mapMode,
            mapStyleUri = mapMode.styleUri,
            visibleObjects = when (visibleBreachObjectsOutput) {
                is Output.Success -> visibleBreachObjectsOutput.value
                is Output.Failure -> emptyList()
            },
            fogOfWar = fogOfWar,
        )
    } else {
        MapBaseUiState.Failure(errorMessage = state.failureMessage)
    }

    private fun State.toMapMode(
        trackingSession: ExplorationTrackingSession,
    ): MapMode {
        val isBreachMode = breachProtocol !is BreachProtocolUiState.Idle

        return if (isBreachMode) {
            MapMode.BreachTactical(
                isLocationAvailable = trackingSession.lastKnownLocation != null,
            )
        } else {
            MapMode.Exploration()
        }
    }

    private fun State.resolveBreachProtocolUiState(
        trackingSession: ExplorationTrackingSession,
    ): BreachProtocolUiState {
        if (breachPhase != BreachSessionPhase.SIGNAL_LOCKED) {
            return breachProtocol
        }

        return lockedBreach?.toSignalLockedUiState(trackingSession.lastKnownLocation) ?: breachProtocol
    }

    private fun MapBaseUiState.toUiState(): MapUiState = when (this) {
        is MapBaseUiState.Failure -> MapUiState.Failure(errorMessage)
        is MapBaseUiState.Content -> {
            MapUiState.Content(
                northResetRequestToken = northResetRequestToken,
                isExplorationTrackingActive = isExplorationTrackingActive,
                explorationTrackingCadence = explorationTrackingCadence,
                explorationTrackingStatus = explorationTrackingStatus,
                breachProtocol = breachProtocol,
                breachGuidance = breachGuidance,
                isStartupSplashVisible = isStartupSplashVisible,
                startupSplashMessage = startupSplashMessage,
                mapMode = mapMode,
                mapStyleUri = mapStyleUri,
                visibleObjects = visibleObjects,
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
        Unit
    }

    private fun onCameraViewportChanged(intent: MapIntent.CameraViewportChanged) {
        _state.update { state ->
            if (state.visibleBounds == intent.visibleBounds) {
                state
            } else {
                state.copy(visibleBounds = intent.visibleBounds)
            }
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

    private suspend fun onObjectTapped(objectId: String) {
        Unit
    }

    private suspend fun onPulseClicked() {
        val actorLocation = currentActorLocation() ?: return
        _state.update { state ->
            state.copy(
                breachProtocol = BreachProtocolUiState.Scanning,
                breachPhase = BreachSessionPhase.SCANNING,
                lockedBreach = null,
                uploadProgressPercent = 0,
            )
        }

        when (val result = findNearestBreachNode(actorLocation)) {
            is Output.Success -> {
                val record = result.value
                val distanceMeters = actorLocation.distanceTo(record.definition.location)
                val canStartUpload = distanceMeters <= record.definition.interactionRadiusMeters
                if (canStartUpload) {
                    discoverBreachNode(
                        id = record.definition.id,
                        actorLocation = actorLocation,
                    )
                }

                val lockedBreach = record.toLockedBreach(
                    distanceMeters = distanceMeters,
                    canStartUpload = canStartUpload,
                )
                val liveTrackingSession = currentTrackingSession() ?: ExplorationTrackingSession(
                    isActive = true,
                    status = ExplorationTrackingStatus.TRACKING,
                    lastKnownLocation = actorLocation,
                )
                _state.update { state ->
                    state.copy(
                        breachProtocol = lockedBreach.resolveSignalLockedUiState(liveTrackingSession),
                        lockedBreach = lockedBreach,
                        uploadProgressPercent = 0,
                        breachPhase = BreachSessionPhase.SIGNAL_LOCKED,
                    )
                }
            }

            is Output.Failure -> {
                _state.update { state ->
                    state.copy(
                        breachProtocol = BreachProtocolUiState.Idle,
                        lockedBreach = null,
                        uploadProgressPercent = 0,
                        breachPhase = BreachSessionPhase.IDLE,
                    )
                }
            }
        }
    }

    private fun onStartBreachUpload() {
        val trackingSession = currentTrackingSession() ?: return
        val lockedBreach = _state.value.lockedBreach ?: return
        val liveSignalLockedUiState = lockedBreach.resolveSignalLockedUiState(trackingSession)
        if (!liveSignalLockedUiState.canStartUpload) {
            return
        }

        _state.update { state ->
            state.copy(
                breachProtocol = BreachProtocolUiState.Uploading(
                    breachNodeId = lockedBreach.definition.id,
                    districtName = lockedBreach.definition.districtName,
                    progressPercent = 0,
                ),
                uploadProgressPercent = 0,
                breachPhase = BreachSessionPhase.UPLOADING,
            )
        }
    }

    private suspend fun onBreachUploadTick() {
        val state = _state.value
        val lockedBreach = state.lockedBreach ?: return
        if (state.breachPhase != BreachSessionPhase.UPLOADING) {
            return
        }

        val nextProgress = (state.uploadProgressPercent + BREACH_UPLOAD_TICK_PERCENT)
            .coerceAtMost(100)

        if (nextProgress < 100) {
            _state.update {
                it.copy(
                    breachProtocol = BreachProtocolUiState.Uploading(
                        breachNodeId = lockedBreach.definition.id,
                        districtName = lockedBreach.definition.districtName,
                        progressPercent = nextProgress,
                    ),
                    uploadProgressPercent = nextProgress,
                )
            }
            return
        }

        val actorLocation = currentActorLocation() ?: return
        when (
            val result = completeBreach(
                id = lockedBreach.definition.id,
                actorLocation = actorLocation,
            )
        ) {
            is Output.Success -> {
                _state.update {
                    it.copy(
                        breachProtocol = BreachProtocolUiState.Completed(
                            districtName = lockedBreach.definition.districtName,
                        ),
                        uploadProgressPercent = 100,
                        breachPhase = BreachSessionPhase.COMPLETED,
                    )
                }
            }

            is Output.Failure -> {
                val trackingSession = currentTrackingSession()
                _state.update {
                    it.copy(
                        breachProtocol = if (trackingSession == null) {
                            lockedBreach.toSignalLockedUiState(
                                distanceMeters = lockedBreach.distanceMeters?.toInt(),
                                canStartUpload = lockedBreach.canStartUpload,
                                disabledReason = if (lockedBreach.canStartUpload) null else "Move closer to start upload.",
                            )
                        } else {
                            lockedBreach.resolveSignalLockedUiState(trackingSession)
                        },
                        uploadProgressPercent = 0,
                        breachPhase = BreachSessionPhase.SIGNAL_LOCKED,
                    )
                }
            }
        }
    }

    private fun onCancelBreachUpload() {
        val trackingSession = currentTrackingSession()
        val lockedBreach = _state.value.lockedBreach
        _state.update {
            if (lockedBreach == null) {
                it.copy(
                    breachProtocol = BreachProtocolUiState.Idle,
                    uploadProgressPercent = 0,
                    breachPhase = BreachSessionPhase.IDLE,
                )
            } else {
                val actorLocation = currentActorLocation()
                it.copy(
                    breachProtocol = if (trackingSession == null) {
                        lockedBreach.toSignalLockedUiState(
                            distanceMeters = lockedBreach.distanceMeters?.toInt(),
                            canStartUpload = lockedBreach.canStartUpload,
                            disabledReason = if (lockedBreach.canStartUpload) null else "Move closer to start upload.",
                        )
                    } else {
                        lockedBreach.resolveSignalLockedUiState(trackingSession)
                    },
                    uploadProgressPercent = 0,
                    breachPhase = BreachSessionPhase.SIGNAL_LOCKED,
                )
            }
        }
    }

    private fun onDismissBreachPanel() {
        _state.update {
            it.copy(
                breachProtocol = BreachProtocolUiState.Idle,
                lockedBreach = null,
                uploadProgressPercent = 0,
                breachPhase = BreachSessionPhase.IDLE,
            )
        }
    }

    private fun currentActorLocation(): GeoPoint? =
        (trackingSession.value as? Output.Success)?.value?.lastKnownLocation

    private fun BreachNodeRecord.toLockedBreach(
        distanceMeters: Double,
        canStartUpload: Boolean,
    ): BreachNode =
        BreachNode(
            definition = definition,
            state = state,
            phase = BreachNodePhase.SIGNAL_LOCKED,
            distanceMeters = distanceMeters,
            canDiscover = canStartUpload,
            canStartUpload = canStartUpload,
        )

    private fun BreachNode.resolveSignalLockedUiState(
        trackingSession: ExplorationTrackingSession,
    ): BreachProtocolUiState.SignalLocked =
        when (val guidance = breachDirectionalGuidancePresenter.present(this, trackingSession.lastKnownLocation)) {
            BreachDirectionalGuidanceUiState.Hidden -> toSignalLockedUiState(
                distanceMeters = distanceMeters?.toInt(),
                canStartUpload = canStartUpload,
                disabledReason = if (canStartUpload) null else "Move closer to start upload.",
            )

            is BreachDirectionalGuidanceUiState.Unavailable -> toSignalLockedUiState(
                distanceMeters = distanceMeters?.toInt(),
                canStartUpload = false,
                disabledReason = guidance.message,
            )

            is BreachDirectionalGuidanceUiState.FloatingArrow -> toSignalLockedUiState(
                distanceMeters = guidance.distanceMeters,
                canStartUpload = guidance.canStartUpload,
                disabledReason = if (guidance.canStartUpload) null else "Move closer to start upload.",
            )

            is BreachDirectionalGuidanceUiState.OnTarget -> toSignalLockedUiState(
                distanceMeters = guidance.distanceMeters,
                canStartUpload = guidance.canStartUpload,
                disabledReason = null,
            )
        }

    private fun BreachNode.toSignalLockedUiState(
        distanceMeters: Int?,
        canStartUpload: Boolean,
        disabledReason: String?,
    ): BreachProtocolUiState.SignalLocked =
        BreachProtocolUiState.SignalLocked(
            breachNodeId = definition.id,
            districtName = definition.districtName,
            distanceMeters = distanceMeters,
            canStartUpload = canStartUpload,
            disabledReason = disabledReason,
        )

    private fun State.resolveBreachGuidanceUiState(
        trackingSession: ExplorationTrackingSession,
    ): BreachDirectionalGuidanceUiState =
        if (breachPhase != BreachSessionPhase.SIGNAL_LOCKED || lockedBreach == null) {
            BreachDirectionalGuidanceUiState.Hidden
        } else {
            breachDirectionalGuidancePresenter.present(
                breach = lockedBreach,
                actorLocation = trackingSession.lastKnownLocation,
            )
        }

    private fun State.resolveBreachProtocolUiState(
        trackingSession: ExplorationTrackingSession,
    ): BreachProtocolUiState =
        if (breachPhase != BreachSessionPhase.SIGNAL_LOCKED || lockedBreach == null) {
            breachProtocol
        } else {
            lockedBreach.resolveSignalLockedUiState(trackingSession)
        }

    private fun currentTrackingSession(): ExplorationTrackingSession? =
        (trackingSession.value as? Output.Success)?.value

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
            val breachProtocol: BreachProtocolUiState,
            val breachGuidance: BreachDirectionalGuidanceUiState,
            val isStartupSplashVisible: Boolean,
            val startupSplashMessage: Int,
            val mapMode: MapMode,
            val mapStyleUri: String,
            val visibleObjects: List<MapObjectUiModel>,
            val fogOfWar: FogOfWarUiState,
        ) : MapBaseUiState
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
        const val BREACH_UPLOAD_TICK_PERCENT = 25
    }
}
