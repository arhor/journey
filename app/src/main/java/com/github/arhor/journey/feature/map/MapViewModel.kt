package com.github.arhor.journey.feature.map

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.github.arhor.journey.R
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.common.resolveMessage
import com.github.arhor.journey.core.ui.MviViewModel
import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.error.StartExplorationTrackingSessionError
import com.github.arhor.journey.domain.usecase.ObserveExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.ObserveSelectedMapStyleUseCase
import com.github.arhor.journey.domain.usecase.StartExplorationTrackingSessionUseCase
import com.github.arhor.journey.feature.map.fow.FogOfWarController
import com.github.arhor.journey.feature.map.fow.model.FogOfWarUiState
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.MapObjectUiModel
import com.github.arhor.journey.feature.map.model.MapViewportSize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val observeSelectedMapStyle: ObserveSelectedMapStyleUseCase,
    private val fogOfWarControllerFactory: FogOfWarController.Factory,
    private val observeExplorationTrackingSession: ObserveExplorationTrackingSessionUseCase,
    private val startExplorationTrackingSession: StartExplorationTrackingSessionUseCase,
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
        ) { state, fogOfWar, trackingSessionOutput, selectedMapStyleOutput ->
            intoBaseUiState(
                state = state,
                trackingSessionOutput = trackingSessionOutput,
                fogOfWar = fogOfWar,
                selectedMapStyleOutput = selectedMapStyleOutput,
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

        MapBaseUiState.Content(
            cameraPosition = state.cameraPosition,
            cameraUpdateOrigin = state.cameraUpdateOrigin,
            isUserInteractingCamera = state.isUserInteractingCamera,
            northResetRequestToken = state.northResetRequestToken,
            isExplorationTrackingActive = trackingSessionValue.isActive,
            explorationTrackingCadence = trackingSessionValue.cadence,
            explorationTrackingStatus = trackingSessionValue.status,
            isStartupSplashVisible = state.startupGate.isSplashVisible,
            startupSplashMessage = R.string.map_view_startup_loading_message,
            mapStyleUri = selectedMapStyle.value,
            visibleObjects = emptyList(),
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
    }
}
