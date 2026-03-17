package com.github.arhor.journey.tracking

import com.github.arhor.journey.di.AppCoroutineScope
import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileGrid
import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfigHolder
import com.github.arhor.journey.domain.usecase.RevealExplorationTilesAtLocationUseCase
import com.github.arhor.journey.tracking.location.UserLocationSource
import com.github.arhor.journey.tracking.location.UserLocationUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExplorationTrackingRuntime @Inject constructor(
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val userLocationSource: UserLocationSource,
    private val revealExplorationTilesAtLocation: RevealExplorationTilesAtLocationUseCase,
    private val configHolder: ExplorationTileRuntimeConfigHolder,
) {
    private val cadence = MutableStateFlow(ExplorationTrackingCadence.BACKGROUND)
    private val session = MutableStateFlow(
        ExplorationTrackingSession(
            cadence = cadence.value,
        )
    )

    private var trackingJob: Job? = null
    private var lastRevealedTiles: Set<ExplorationTile> = emptySet()

    fun observeSession(): Flow<ExplorationTrackingSession> = session.asStateFlow()

    fun snapshot(): ExplorationTrackingSession = session.value

    fun markStarting() {
        session.update {
            it.copy(
                isActive = true,
                status = ExplorationTrackingStatus.STARTING,
                cadence = cadence.value,
            )
        }
    }

    fun markPermissionDenied() {
        session.update {
            it.copy(
                isActive = false,
                status = ExplorationTrackingStatus.PERMISSION_DENIED,
            )
        }
    }

    fun setCadence(value: ExplorationTrackingCadence) {
        cadence.value = value
        session.update {
            it.copy(cadence = value)
        }
    }

    fun startIfNeeded() {
        if (trackingJob != null) {
            return
        }

        session.update {
            it.copy(
                isActive = true,
                status = ExplorationTrackingStatus.STARTING,
                cadence = cadence.value,
            )
        }

        trackingJob = appScope.launch {
            userLocationSource.observeLocations(cadence)
                .catch {
                    session.update { current ->
                        current.copy(
                            isActive = true,
                            status = ExplorationTrackingStatus.TEMPORARILY_UNAVAILABLE,
                        )
                    }
                }
                .collect(::handleLocationUpdate)
        }
    }

    fun stop() {
        trackingJob?.cancel()
        trackingJob = null
        lastRevealedTiles = emptySet()

        session.update {
            it.copy(
                isActive = false,
                status = ExplorationTrackingStatus.INACTIVE,
            )
        }
    }

    private suspend fun handleLocationUpdate(update: UserLocationUpdate) {
        when (update) {
            is UserLocationUpdate.Available -> onLocationAvailable(update)
            UserLocationUpdate.LocationServicesDisabled -> {
                session.update {
                    it.copy(
                        isActive = true,
                        status = ExplorationTrackingStatus.LOCATION_SERVICES_DISABLED,
                    )
                }
            }

            UserLocationUpdate.PermissionDenied -> {
                session.update {
                    it.copy(
                        isActive = false,
                        status = ExplorationTrackingStatus.PERMISSION_DENIED,
                    )
                }
            }

            UserLocationUpdate.TemporarilyUnavailable -> {
                session.update {
                    it.copy(
                        isActive = true,
                        status = ExplorationTrackingStatus.TEMPORARILY_UNAVAILABLE,
                    )
                }
            }
        }
    }

    private suspend fun onLocationAvailable(update: UserLocationUpdate.Available) {
        session.update {
            it.copy(
                isActive = true,
                status = ExplorationTrackingStatus.TRACKING,
                lastKnownLocation = update.location,
            )
        }

        val config = configHolder.snapshot()
        val revealTiles = ExplorationTileGrid.revealTilesAround(
            point = update.location,
            radiusMeters = config.revealRadiusMeters,
            zoom = config.canonicalZoom,
        )
        if (revealTiles == lastRevealedTiles) {
            return
        }

        revealExplorationTilesAtLocation(update.location)
        lastRevealedTiles = revealTiles
    }
}
