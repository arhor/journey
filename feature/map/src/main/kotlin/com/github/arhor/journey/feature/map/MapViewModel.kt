package com.github.arhor.journey.feature.map

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.github.arhor.journey.core.common.DomainError
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.core.common.combine as combineOutputs
import com.github.arhor.journey.core.common.fold
import com.github.arhor.journey.core.common.map
import com.github.arhor.journey.core.common.resolveMessage
import com.github.arhor.journey.core.ui.MviViewModel
import com.github.arhor.journey.domain.internal.tileAt
import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfig
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.PointOfInterest
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.Watchtower
import com.github.arhor.journey.domain.model.WatchtowerPhase
import com.github.arhor.journey.domain.model.WatchtowerResourceCost
import com.github.arhor.journey.domain.model.error.ClaimWatchtowerError
import com.github.arhor.journey.domain.model.error.StartExplorationTrackingSessionError
import com.github.arhor.journey.domain.model.error.UpgradeWatchtowerError
import com.github.arhor.journey.domain.usecase.DiscoverPointOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ClaimWatchtowerUseCase
import com.github.arhor.journey.domain.usecase.GetWatchtowerUseCase
import com.github.arhor.journey.domain.usecase.GetExplorationTileRuntimeConfigUseCase
import com.github.arhor.journey.domain.usecase.ObserveCollectibleResourceSpawnsUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationProgressUseCase
import com.github.arhor.journey.domain.usecase.ObserveHeroResourceAmountUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.ObservePointsOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveSelectedMapStyleUseCase
import com.github.arhor.journey.domain.usecase.ObserveVisibleWatchtowersUseCase
import com.github.arhor.journey.domain.usecase.StartExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.UpgradeWatchtowerUseCase
import com.github.arhor.journey.feature.map.fow.FogOfWarController
import com.github.arhor.journey.feature.map.fow.model.FogOfWarUiState
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapObjectKind
import com.github.arhor.journey.feature.map.model.MapObjectUiModel
import com.github.arhor.journey.feature.map.model.MapViewportSize
import com.github.arhor.journey.feature.map.model.WatchtowerMarkerState
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
import kotlin.math.roundToInt

private const val DEFAULT_CAMERA_ZOOM = 17.0
private const val DEFAULT_CAMERA_BEARING = 0.0
private const val RESOURCE_QUERY_BUFFER_FRACTION = 0.5
private const val MIN_LONGITUDE = -180.0
private const val MAX_LONGITUDE = 180.0 - 1e-9
private const val MIN_LATITUDE = -85.05112878
private const val MAX_LATITUDE = 85.05112878
@Immutable
private data class State(
    val cameraPosition: CameraPositionState? = null,
    val cameraUpdateOrigin: CameraUpdateOrigin = CameraUpdateOrigin.PROGRAMMATIC,
    val isUserInteractingCamera: Boolean = false,
    val northResetRequestToken: Int = 0,
    val isAwaitingLocationPermissionResult: Boolean = false,
    val visibleBounds: GeoBounds? = null,
    val viewportSize: MapViewportSize? = null,
    val resourceQueryBounds: GeoBounds? = null,
    val addPoiAnchor: LatLng? = null,
    val selectedWatchtowerId: String? = null,
    val selectedWatchtowerSnapshot: Watchtower? = null,
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
    private val observeVisibleWatchtowers: ObserveVisibleWatchtowersUseCase,
    private val observeHeroResourceAmount: ObserveHeroResourceAmountUseCase,
    private val claimWatchtower: ClaimWatchtowerUseCase,
    private val upgradeWatchtower: UpgradeWatchtowerUseCase,
    private val getWatchtower: GetWatchtowerUseCase,
    private val getExplorationTileRuntimeConfig: GetExplorationTileRuntimeConfigUseCase,
    private val fogOfWarControllerFactory: FogOfWarController.Factory,
    private val observeExplorationTrackingSession: ObserveExplorationTrackingSessionUseCase,
    private val startExplorationTrackingSession: StartExplorationTrackingSessionUseCase,
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
    private var cachedVisibleObjects: List<MapObjectUiModel> = emptyList()

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
                    trackingSessionOutput = session,
                    mapStyleOutput = mapStyleOutput,
                    pointsOfInterestOutput = pointsOfInterest,
                )
            },
            watchtowerResourceAmounts,
            observeVisibleWorldObjects(),
        ) { inputs, watchtowerResourceAmounts, visibleWorldObjects ->
            intoUiState(
                state = inputs.state,
                trackingSessionOutput = inputs.trackingSessionOutput,
                mapStyleOutput = inputs.mapStyleOutput,
                pointsOfInterestOutput = inputs.pointsOfInterestOutput,
                fogOfWar = inputs.fogOfWar,
                visibleWorldObjectsOutput = visibleWorldObjects,
                watchtowerResourceAmountsOutput = watchtowerResourceAmounts,
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

    private fun observeVisibleWorldObjects(): Flow<Output<VisibleWorldObjects, DomainError>> =
        combine(
            observePointOfInterestObjects(),
            observeVisibleResourceSpawnObjects(),
            visibleWatchtowerData,
        ) { pointOfInterestObjects, resourceSpawnObjects, watchtowerData ->
            combineOutputs(
                combineOutputs(pointOfInterestObjects, resourceSpawnObjects),
                watchtowerData,
            ) { (pointOfInterestObjectsValue, resourceSpawnObjectsValue), watchtowerDataValue ->
                VisibleWorldObjects(
                    objects = pointOfInterestObjectsValue + resourceSpawnObjectsValue + watchtowerDataValue.objects,
                    watchtowers = watchtowerDataValue.watchtowers,
                )
            }
        }
            .distinctUntilChanged()

    private fun observePointOfInterestObjects(): Flow<Output<List<MapObjectUiModel>, DomainError>> =
        combine(
            observePointsOfInterest(),
            observeExplorationProgress(),
        ) { pointsOfInterestOutput, explorationProgressOutput ->
            combineOutputs(pointsOfInterestOutput, explorationProgressOutput) { pointsOfInterest, explorationProgress ->
                val discoveredPoiIds = explorationProgress.discovered
                    .mapTo(mutableSetOf()) { it.poiId }

                pointsOfInterest.map { pointOfInterest ->
                    pointOfInterest.toUiModel(isDiscovered = pointOfInterest.id in discoveredPoiIds)
                }
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

                val visibilityTileMask = fogVisibility.visibilityTileMask

                resourceDerivedData.resourceSpawns.map { resourceSpawn ->
                    resourceSpawn.toUiModel(
                        isHiddenByFog = resourceSpawn.isHiddenByFog(
                            canonicalZoom = canonicalZoom,
                            visibilityTileMask = visibilityTileMask,
                        ),
                    )
                }
            }
        }
            .distinctUntilChanged()

    private fun observeVisibleWatchtowerData(): Flow<Output<VisibleWatchtowerData, DomainError>> =
        combine(
            _state.map { it.visibleBounds }.distinctUntilChanged(),
            trackingSession,
            watchtowerResourceAmounts,
        ) { visibleBounds, trackingSessionOutput, resourceAmountsOutput ->
            Triple(visibleBounds, trackingSessionOutput, resourceAmountsOutput)
        }
            .flatMapLatest { (visibleBounds, trackingSessionOutput, resourceAmountsOutput) ->
                visibleBounds
                    ?.let { bounds ->
                        val interactionContext = when (val result = combineOutputs(trackingSessionOutput, resourceAmountsOutput)) {
                            is Output.Success -> result.value
                            is Output.Failure -> return@flatMapLatest flowOf(Output.Failure(result.error))
                        }

                        observeVisibleWatchtowers(bounds).map { watchtowersOutput ->
                            watchtowersOutput.map { watchtowers ->
                                val trackingSessionValue = interactionContext.first
                                val resourceAmounts = interactionContext.second
                                val decoratedWatchtowers = watchtowers.map { watchtower ->
                                    watchtower.withInteractionContext(
                                        actorLocation = trackingSessionValue.lastKnownLocation,
                                        resourceAmounts = resourceAmounts,
                                    )
                                }

                                VisibleWatchtowerData(
                                    watchtowers = decoratedWatchtowers,
                                    objects = decoratedWatchtowers.map { it.toUiModel() },
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
            .map { state -> state.resourceQueryBounds }
            .distinctUntilChanged()
            .flatMapLatest { queryBounds ->
                observeVisibleResourceSpawns(queryBounds)
                    .map { resourceSpawnsOutput ->
                        resourceSpawnsOutput.map { resourceSpawns ->
                            ResourceDerivedData(
                                queryBounds = queryBounds,
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
            is MapIntent.CameraViewportChanged -> onCameraViewportChanged(intent)
            is MapIntent.MapViewportSizeChanged -> onMapViewportSizeChanged(intent)
            is MapIntent.CameraGestureStarted -> onCameraGestureStarted(intent)
            is MapIntent.CameraSettled -> onCameraSettled(intent)
            is MapIntent.CurrentLocationUnavailable -> onCurrentLocationUnavailable()
            is MapIntent.LocationPermissionResult -> onLocationPermissionResult(intent)
            is MapIntent.MapTapped -> onMapTapped(intent)
            is MapIntent.RecenterClicked -> onRecenterClicked()
            is MapIntent.ObjectTapped -> onObjectTapped(intent.objectId)
            MapIntent.DismissWatchtowerSheet -> onDismissWatchtowerSheet()
            MapIntent.ClaimSelectedWatchtower -> onClaimSelectedWatchtower()
            MapIntent.UpgradeSelectedWatchtower -> onUpgradeSelectedWatchtower()
            is MapIntent.AddPoiClicked -> onAddPoiClicked()
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

    private fun intoUiState(
        state: State,
        trackingSessionOutput: Output<ExplorationTrackingSession, DomainError>,
        mapStyleOutput: Output<MapStyle?, DomainError>,
        pointsOfInterestOutput: Output<List<PointOfInterest>, DomainError>,
        fogOfWar: FogOfWarUiState,
        visibleWorldObjectsOutput: Output<VisibleWorldObjects, DomainError>,
        watchtowerResourceAmountsOutput: Output<Map<String, Int>, DomainError>,
    ): MapUiState = if (state.failureMessage == null) {
        val trackingSession = when (trackingSessionOutput) {
            is Output.Success -> trackingSessionOutput.value
            is Output.Failure -> {
                return MapUiState.Failure(
                    errorMessage = trackingSessionOutput.error.resolveMessage(MAP_LOADING_FAILED_MESSAGE),
                )
            }
        }
        val selectedMapStyle = when (mapStyleOutput) {
            is Output.Success -> mapStyleOutput.value
            is Output.Failure -> {
                return MapUiState.Failure(
                    errorMessage = mapStyleOutput.error.resolveMessage(MAP_LOADING_FAILED_MESSAGE),
                )
            }
        }
        val pointsOfInterest = when (pointsOfInterestOutput) {
            is Output.Success -> pointsOfInterestOutput.value
            is Output.Failure -> {
                return MapUiState.Failure(
                    errorMessage = pointsOfInterestOutput.error.resolveMessage(MAP_LOADING_FAILED_MESSAGE),
                )
            }
        }
        val visibleWorldObjects = when (visibleWorldObjectsOutput) {
            is Output.Success -> visibleWorldObjectsOutput.value
            is Output.Failure -> {
                return MapUiState.Failure(
                    errorMessage = visibleWorldObjectsOutput.error.resolveMessage(MAP_LOADING_FAILED_MESSAGE),
                )
            }
        }
        val watchtowerResourceAmounts = when (watchtowerResourceAmountsOutput) {
            is Output.Success -> watchtowerResourceAmountsOutput.value
            is Output.Failure -> {
                return MapUiState.Failure(
                    errorMessage = watchtowerResourceAmountsOutput.error.resolveMessage(MAP_LOADING_FAILED_MESSAGE),
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
                            ?.withInteractionContext(
                                actorLocation = trackingSession.lastKnownLocation,
                                resourceAmounts = watchtowerResourceAmounts,
                            )
                    )
                    ?.toSheetUiState(watchtowerResourceAmounts)
            }
        val userLocation = trackingSession.lastKnownLocation?.toLatLng()
        val resolvedCameraPosition = userLocation?.toCameraPosition(
            zoom = state.cameraPosition?.zoom ?: DEFAULT_CAMERA_ZOOM,
            bearing = state.cameraPosition?.bearing ?: DEFAULT_CAMERA_BEARING,
        )
            ?: state.cameraPosition
            ?: pointsOfInterest.defaultCameraPosition()
        val resolvedCameraUpdateOrigin = if (userLocation != null && !state.isUserInteractingCamera) {
            CameraUpdateOrigin.PROGRAMMATIC
        } else {
            state.cameraUpdateOrigin
        }

        MapUiState.Content(
            cameraPosition = resolvedCameraPosition,
            cameraUpdateOrigin = resolvedCameraUpdateOrigin,
            northResetRequestToken = state.northResetRequestToken,
            userLocation = userLocation,
            isExplorationTrackingActive = trackingSession.isActive,
            explorationTrackingCadence = trackingSession.cadence,
            explorationTrackingStatus = trackingSession.status,
            selectedStyle = selectedMapStyle,
            visibleObjects = resolvedVisibleObjects,
            selectedWatchtower = selectedWatchtower,
            fogOfWar = fogOfWar,
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

    private fun onMapTapped(intent: MapIntent.MapTapped) {
        _state.update {
            it.copy(
                addPoiAnchor = intent.target,
                selectedWatchtowerId = null,
                selectedWatchtowerSnapshot = null,
            )
        }
    }

    private fun onAddPoiClicked() {
        val contentState = uiState.value as? MapUiState.Content ?: return
        val target = _state.value.addPoiAnchor
            ?: contentState.userLocation
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
        _state.update {
            it.copy(
                cameraPosition = intent.position,
                cameraUpdateOrigin = intent.origin,
                isUserInteractingCamera = false,
            )
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
        val parsedId = parseMapObjectId(objectUiModel.id) ?: return

        try {
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

            if (parsedId.kind == MapObjectKind.PointOfInterest) {
                val poiId = parsedId.rawId.toLongOrNull() ?: return
                when (val result = discoverPointOfInterest(poiId)) {
                    is Output.Success -> {
                        emitEffect(MapEffect.OpenObjectDetails(parsedId.rawId))
                    }

                    is Output.Failure -> {
                        emitEffect(
                            MapEffect.ShowMessage(
                                result.error.resolveMessage(OBJECT_DISCOVERY_FAILED_MESSAGE),
                            ),
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            emitEffect(MapEffect.ShowMessage(e.message ?: OBJECT_DISCOVERY_FAILED_MESSAGE))
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
                    emitEffect(MapEffect.ShowMessage(costRequirementMessage(error.resourceTypeId)))
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
                    emitEffect(MapEffect.ShowMessage(costRequirementMessage(error.resourceTypeId)))
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

    private fun ResourceSpawn.toUiModel(isHiddenByFog: Boolean): MapObjectUiModel {
        val resourceType = ResourceType.fromTypeId(typeId)

        return MapObjectUiModel(
            id = mapObjectId(
                kind = MapObjectKind.ResourceSpawn,
                rawId = id,
            ),
            kind = MapObjectKind.ResourceSpawn,
            title = resourceType?.displayName ?: typeId,
            description = null,
            position = position.toLatLng(),
            radiusMeters = collectionRadiusMeters.toInt(),
            isDiscovered = false,
            isHiddenByFog = isHiddenByFog,
            resourceType = resourceType,
        )
    }

    private fun Watchtower.toUiModel(): MapObjectUiModel =
        MapObjectUiModel(
            id = mapObjectId(
                kind = MapObjectKind.Watchtower,
                rawId = id,
            ),
            kind = MapObjectKind.Watchtower,
            title = name,
            description = description,
            position = location.toLatLng(),
            radiusMeters = (revealRadiusMeters ?: interactionRadiusMeters).roundToInt(),
            isDiscovered = true,
            watchtowerMarkerState = toMarkerState(),
            watchtowerLevel = level ?: 1,
        )

    private fun Watchtower.withInteractionContext(
        actorLocation: GeoPoint?,
        resourceAmounts: Map<String, Int>,
    ): Watchtower {
        val distanceMeters = actorLocation?.distanceTo(location)
        val isInRange = distanceMeters != null && distanceMeters <= interactionRadiusMeters
        val claimAffordable = claimCost?.isAffordable(resourceAmounts) ?: true
        val upgradeAffordable = nextUpgradeCost?.isAffordable(resourceAmounts) ?: true

        return copy(
            canClaim = phase == WatchtowerPhase.DISCOVERED_DORMANT && isInRange && claimAffordable,
            canUpgrade = phase == WatchtowerPhase.CLAIMED && nextUpgradeCost != null && isInRange && upgradeAffordable,
            distanceMeters = distanceMeters,
        )
    }

    private fun Watchtower.toSheetUiState(
        resourceAmounts: Map<String, Int>,
    ): WatchtowerSheetUiState {
        val distanceMeters = distanceMeters
        val nextUpgradeCost = nextUpgradeCost
        val isInRange = distanceMeters != null && distanceMeters <= interactionRadiusMeters
        val claimAffordable = claimCost?.isAffordable(resourceAmounts) ?: true
        val upgradeAffordable = nextUpgradeCost?.isAffordable(resourceAmounts) ?: true

        return WatchtowerSheetUiState(
            id = id,
            title = name,
            description = description,
            phase = when (phase) {
                WatchtowerPhase.DISCOVERED_DORMANT -> WatchtowerSheetPhase.DISCOVERED_DORMANT
                WatchtowerPhase.CLAIMED -> WatchtowerSheetPhase.CLAIMED
            },
            level = level,
            revealRadiusMeters = revealRadiusMeters?.roundToInt(),
            nextRevealRadiusMeters = nextRevealRadiusMeters?.roundToInt(),
            distanceMeters = distanceMeters?.roundToInt(),
            claimCostLabel = claimCost?.toDisplayLabel(),
            upgradeCostLabel = nextUpgradeCost?.toDisplayLabel(),
            canClaim = canClaim && claimAffordable,
            canUpgrade = canUpgrade && upgradeAffordable,
            claimDisabledReason = when {
                phase != WatchtowerPhase.DISCOVERED_DORMANT -> null
                isInRange && !claimAffordable -> costRequirementMessage(claimCost?.resourceTypeId)
                isInRange -> null
                distanceMeters != null -> WATCHTOWER_MOVE_CLOSER_MESSAGE
                else -> CURRENT_LOCATION_UNAVAILABLE_MESSAGE
            },
            upgradeDisabledReason = when {
                phase != WatchtowerPhase.CLAIMED || nextUpgradeCost == null -> null
                isInRange && !upgradeAffordable -> costRequirementMessage(nextUpgradeCost.resourceTypeId)
                isInRange -> null
                distanceMeters != null -> WATCHTOWER_MOVE_CLOSER_MESSAGE
                else -> CURRENT_LOCATION_UNAVAILABLE_MESSAGE
            },
            isAtMaxLevel = phase == WatchtowerPhase.CLAIMED && nextUpgradeCost == null,
        )
    }

    private fun Watchtower.toMarkerState(): WatchtowerMarkerState = when {
        phase == WatchtowerPhase.CLAIMED && canUpgrade -> WatchtowerMarkerState.UPGRADE_AVAILABLE
        phase == WatchtowerPhase.CLAIMED -> WatchtowerMarkerState.CLAIMED
        canClaim -> WatchtowerMarkerState.CLAIMABLE
        else -> WatchtowerMarkerState.DISCOVERED_DORMANT
    }

    private fun WatchtowerResourceCost.toDisplayLabel(): String {
        val resourceLabel = ResourceType.fromTypeId(resourceTypeId)?.displayName ?: resourceTypeId
        return "$amount $resourceLabel"
    }

    private fun WatchtowerResourceCost.isAffordable(resourceAmounts: Map<String, Int>): Boolean =
        (resourceAmounts[resourceTypeId] ?: 0) >= amount

    private fun ResourceSpawn.isHiddenByFog(
        canonicalZoom: Int,
        visibilityTileMask: Set<MapTile>,
    ): Boolean = tileAt(
        point = position,
        zoom = canonicalZoom,
    ) !in visibilityTileMask

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

    private fun currentTrackingSession(): ExplorationTrackingSession? =
        when (val result = trackingSession.value) {
            is Output.Success -> result.value
            is Output.Failure -> null
        }

    private fun costRequirementMessage(resourceTypeId: String?): String {
        val fallbackTypeId = resourceTypeId ?: "materials"
        val resourceLabel = ResourceType.fromTypeId(fallbackTypeId)?.displayName ?: fallbackTypeId
        return "Not enough $resourceLabel."
    }

    private fun GeoPoint.toLatLng(): LatLng =
        LatLng(
            latitude = lat,
            longitude = lon,
        )

    private fun LatLng.toCameraPosition(
        zoom: Double = DEFAULT_CAMERA_ZOOM,
        bearing: Double = DEFAULT_CAMERA_BEARING,
    ): CameraPositionState =
        CameraPositionState(
            target = this,
            zoom = zoom,
            bearing = bearing,
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
            bearing = DEFAULT_CAMERA_BEARING,
        )
    }

    @Immutable
    private data class UiStateInputs(
        val state: State,
        val fogOfWar: FogOfWarUiState,
        val trackingSessionOutput: Output<ExplorationTrackingSession, DomainError>,
        val mapStyleOutput: Output<MapStyle?, DomainError>,
        val pointsOfInterestOutput: Output<List<PointOfInterest>, DomainError>,
    )

    @Immutable
    private data class ResourceDerivedData(
        val queryBounds: GeoBounds?,
        val resourceSpawns: List<ResourceSpawn>,
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
        const val WATCHTOWER_MOVE_CLOSER_MESSAGE = "Move closer to interact."
        const val MAP_OBJECT_ID_SEPARATOR = ":"
    }
}
