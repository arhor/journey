package com.github.arhor.journey.ui.views.map

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.github.arhor.journey.core.logging.LoggerFactory
import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.model.ExplorationProgress
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.PointOfInterest
import com.github.arhor.journey.domain.usecase.DiscoverPointOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationProgressUseCase
import com.github.arhor.journey.domain.usecase.ObservePointsOfInterestUseCase
import com.github.arhor.journey.domain.usecase.ObserveSettingsUseCase
import com.github.arhor.journey.ui.MviViewModel
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
    val styleLoadErrorMessage: String? = null,
    val styleReloadToken: Int = 0,
)

@Stable
@HiltViewModel
class MapViewModel @Inject constructor(
    private val observePointsOfInterest: ObservePointsOfInterestUseCase,
    private val observeExplorationProgress: ObserveExplorationProgressUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    private val discoverPointOfInterest: DiscoverPointOfInterestUseCase,
    private val mapStyleRepository: MapStyleRepository,
    loggerFactory: LoggerFactory,
) : MviViewModel<MapUiState, MapEffect, MapIntent>(
    loggerFactory = loggerFactory,
    initialState = MapUiState.Loading,
) {
    private val _state = MutableStateFlow(State())

    override fun buildUiState(): Flow<MapUiState> =
        combine(
            _state,
            observeSettings(),
            observePointsOfInterest(),
            observeExplorationProgress(),
            ::intoUiState,
        ).catch { error ->
            emit(
                failureUiState(
                    state = _state.value,
                    selectedStyle = MapStyle.DEFAULT,
                    errorMessage = error.message ?: MAP_LOADING_FAILED_MESSAGE,
                ),
            )
        }.distinctUntilChanged()

    override suspend fun handleIntent(intent: MapIntent) {
        when (intent) {
            is MapIntent.OnCameraSettled -> _state.update {
                it.copy(
                    cameraPosition = intent.position,
                    cameraUpdateOrigin = intent.origin,
                )
            }

            is MapIntent.OnMapTapped -> _state.update {
                it.copy(
                    cameraPosition = CameraPositionState(
                        target = intent.target,
                        zoom = it.cameraPosition.zoom,
                    ),
                    cameraUpdateOrigin = CameraUpdateOrigin.PROGRAMMATIC,
                )
            }

            MapIntent.OnRecenterClicked -> {
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
            is MapIntent.OnObjectTapped -> handleObjectTapped(intent.objectId)
            is MapIntent.OnMapLoadFailed -> _state.update {
                it.copy(styleLoadErrorMessage = intent.message ?: MAP_STYLE_LOADING_FAILED_MESSAGE)
            }
            MapIntent.RetryStyleLoad -> _state.update {
                it.copy(
                    styleLoadErrorMessage = null,
                    styleReloadToken = it.styleReloadToken + 1,
                )
            }
        }
    }

    private suspend fun handleObjectTapped(objectId: String) {
        try {
            uiState.value.visibleObjects
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
        pointsOfInterest: List<PointOfInterest>,
        explorationProgress: ExplorationProgress,
    ): MapUiState {
        val selectedStyle = settings.mapStyle
        val resolvedStyle = mapStyleRepository.resolve(selectedStyle)
        val visibleObjects = mapObjects(
            pointsOfInterest = pointsOfInterest,
            explorationProgress = explorationProgress,
        )

        return MapUiState(
            cameraPosition = state.cameraPosition,
            cameraUpdateOrigin = state.cameraUpdateOrigin,
            selectedStyle = selectedStyle,
            resolvedStyle = resolvedStyle,
            styleLoadErrorMessage = state.styleLoadErrorMessage,
            styleReloadToken = state.styleReloadToken,
            visibleObjects = visibleObjects,
            isLoading = false,
            errorMessage = null,
        )
    }

    private fun failureUiState(
        state: State,
        selectedStyle: MapStyle,
        errorMessage: String,
    ): MapUiState {
        return MapUiState(
            cameraPosition = state.cameraPosition,
            cameraUpdateOrigin = state.cameraUpdateOrigin,
            selectedStyle = selectedStyle,
            resolvedStyle = mapStyleRepository.resolve(selectedStyle),
            styleLoadErrorMessage = state.styleLoadErrorMessage,
            styleReloadToken = state.styleReloadToken,
            visibleObjects = emptyList(),
            isLoading = false,
            errorMessage = errorMessage,
        )
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

    private companion object {
        const val SETTINGS_LOADING_FAILED_MESSAGE = "Failed to load settings."
        const val POINTS_LOADING_FAILED_MESSAGE = "Failed to load map objects."
        const val EXPLORATION_LOADING_FAILED_MESSAGE = "Failed to load exploration progress."
        const val MAP_LOADING_FAILED_MESSAGE = "Failed to load map state."
        const val OBJECT_DISCOVERY_FAILED_MESSAGE = "Failed to open map object details."
        const val MAP_STYLE_LOADING_FAILED_MESSAGE = "Failed to load map style."
    }
}
