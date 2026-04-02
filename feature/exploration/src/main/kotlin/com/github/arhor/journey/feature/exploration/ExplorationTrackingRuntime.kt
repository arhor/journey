package com.github.arhor.journey.feature.exploration

import com.github.arhor.journey.di.AppCoroutineScope
import com.github.arhor.journey.domain.ExplorationTileRuntimeConfigHolder
import com.github.arhor.journey.domain.internal.revealTilesAround
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.usecase.CollectNearbyResourceSpawnsUseCase
import com.github.arhor.journey.domain.usecase.DiscoverWatchtowersByClearedTilesUseCase
import com.github.arhor.journey.domain.usecase.RevealExplorationTilesAtLocationUseCase
import com.github.arhor.journey.feature.exploration.location.UserLocationSource
import com.github.arhor.journey.feature.exploration.location.UserLocationUpdate
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
import kotlin.math.roundToInt

private const val NEARBY_COLLECTION_BUCKET_SCALE = 10_000.0

@Singleton
class ExplorationTrackingRuntime @Inject constructor(
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val userLocationSource: UserLocationSource,
    private val revealExplorationTilesAtLocation: RevealExplorationTilesAtLocationUseCase,
    private val discoverWatchtowersByClearedTiles: DiscoverWatchtowersByClearedTilesUseCase,
    private val collectNearbyResourceSpawns: CollectNearbyResourceSpawnsUseCase,
    private val configHolder: ExplorationTileRuntimeConfigHolder,
) {
    private val cadence = MutableStateFlow(ExplorationTrackingCadence.BACKGROUND)
    private val session = MutableStateFlow(
        ExplorationTrackingSession(
            cadence = cadence.value,
        )
    )

    private var trackingJob: Job? = null
    private var lastRevealedTiles: Set<MapTile> = emptySet()
    private var lastNearbyCollectionBucket: NearbyCollectionBucket? = null

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
        lastNearbyCollectionBucket = null

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

        maybeCollectNearbyResources(location = update.location)

        val config = configHolder.snapshot()
        val revealTiles = revealTilesAround(
            point = update.location,
            radiusMeters = config.revealRadiusMeters,
            zoom = config.canonicalZoom,
        )
        if (revealTiles == lastRevealedTiles) {
            return
        }

        val newlyClearedTiles = revealExplorationTilesAtLocation(update.location)
        if (newlyClearedTiles.isNotEmpty()) {
            discoverWatchtowersByClearedTiles(newlyClearedTiles)
        }
        lastRevealedTiles = revealTiles
    }

    private suspend fun maybeCollectNearbyResources(location: GeoPoint) {
        val bucket = location.toNearbyCollectionBucket()
        if (bucket == lastNearbyCollectionBucket) {
            return
        }

        runCatching {
            collectNearbyResourceSpawns(location)
        }.onSuccess {
            lastNearbyCollectionBucket = bucket
        }
    }

    private fun GeoPoint.toNearbyCollectionBucket(): NearbyCollectionBucket =
        NearbyCollectionBucket(
            latBucket = (lat * NEARBY_COLLECTION_BUCKET_SCALE).roundToInt(),
            lonBucket = (lon * NEARBY_COLLECTION_BUCKET_SCALE).roundToInt(),
        )

    private data class NearbyCollectionBucket(
        val latBucket: Int,
        val lonBucket: Int,
    )
}
