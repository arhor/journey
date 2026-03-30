package com.github.arhor.journey.feature.map.fow

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.github.arhor.journey.domain.CANONICAL_ZOOM
import com.github.arhor.journey.domain.internal.revealTilesAround
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.domain.usecase.GetExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTileRuntimeConfigUseCase
import com.github.arhor.journey.domain.usecase.ObserveExploredTilesUseCase
import com.github.arhor.journey.feature.map.fow.model.FogBufferRegion
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderData
import com.github.arhor.journey.feature.map.fow.model.FogOfWarUiState
import com.github.arhor.journey.feature.map.fow.model.FogOfWarVisibilityState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_VISIBLE_FOG_TILE_COUNT = 8_192L
private const val MAX_BUFFERED_FOG_TILE_COUNT = MAX_VISIBLE_FOG_TILE_COUNT * 9

@Stable
class FogOfWarController @AssistedInject constructor(
    private val observeExplorationTileRuntimeConfig: ObserveExplorationTileRuntimeConfigUseCase,
    private val observeExplorationTrackingSession: ObserveExplorationTrackingSessionUseCase,
    private val observeExploredTiles: ObserveExploredTilesUseCase,
    private val getExploredTiles: GetExploredTilesUseCase,
    private val renderDataFactory: FowRenderDataFactory,
    private val fogOfWarCalculator: FogOfWarCalculator,
    @Assisted private val scope: CoroutineScope,
) {
    @AssistedFactory
    fun interface Factory {
        fun create(scope: CoroutineScope): FogOfWarController
    }

    private val _state = MutableStateFlow(State())
    private var displayedFogObservationJob: Job? = null
    private var pendingFogProcessingJob: Job? = null
    private var displayedVisibilityRefreshJob: Job? = null
    private var latestPendingFogBuffer: FogBufferRegion? = null

    val uiState: Flow<FogOfWarUiState> = _state
        .map(::buildUiState)
        .distinctUntilChanged()

    val visibilityState: Flow<FogOfWarVisibilityState> = _state
        .map(::buildVisibilityState)
        .distinctUntilChanged()

    init {
        scope.launch {
            combine(
                observeExplorationTileRuntimeConfig(),
                observeExplorationTrackingSession(),
            ) { config, session ->
                VisibilityStateSnapshot(
                    canonicalZoom = config.canonicalZoom,
                    visibilityTileMask = session.toVisibilityTileMask(
                        canonicalZoom = config.canonicalZoom,
                        revealRadiusMeters = config.revealRadiusMeters,
                    ),
                )
            }
                .distinctUntilChanged()
                .collectLatest { snapshot ->
                    val currentState = _state.value
                    val zoomChanged = currentState.canonicalZoom != snapshot.canonicalZoom
                    val visibilityMaskChanged = currentState.visibilityTileMask != snapshot.visibilityTileMask

                    _state.update { current ->
                        current.copy(
                            canonicalZoom = snapshot.canonicalZoom,
                            visibilityTileMask = snapshot.visibilityTileMask,
                        )
                    }

                    when {
                        zoomChanged -> {
                            _state.value.visibleBounds?.let { visibleBounds ->
                                updateFogViewport(
                                    visibleBounds = visibleBounds,
                                    forceDisplayedBufferReplacement = true,
                                )
                            }
                        }

                        visibilityMaskChanged -> {
                            refreshDisplayedVisibilityData()
                        }
                    }
                }
        }
    }

    fun setOverlayEnabled(isEnabled: Boolean) {
        if (_state.value.isOverlayEnabled == isEnabled) {
            return
        }

        _state.update {
            it.copy(isOverlayEnabled = isEnabled)
        }
    }

    fun updateViewport(visibleBounds: GeoBounds) {
        updateFogViewport(visibleBounds = visibleBounds)
    }

    private fun observeFogExploredTiles(
        fogTileRange: ExplorationTileRange?,
    ): Flow<Set<MapTile>> = fogTileRange
        ?.let(observeExploredTiles::invoke)
        ?: flowOf(emptySet())

    private suspend fun prepareFogBufferData(
        buffer: FogBufferRegion,
        exploredTiles: Set<MapTile>,
        visibilityTileMask: Set<MapTile>,
    ): PreparedFogBuffer {
        // Fog geometry is CPU-bound; keep it off the main dispatcher.
        return withContext(Dispatchers.Default) {
            val coroutineContext = currentCoroutineContext()
            val diagnosticsEnabled = BuildConfig.DEBUG
            val fogRanges = fogOfWarCalculator.calculateUnexploredFogRanges(
                tileRange = buffer.bufferedTileRange,
                exploredTiles = exploredTiles,
            )

            coroutineContext.ensureActive()

            val renderOutput = if (diagnosticsEnabled) {
                renderDataFactory.createDetailed(fogRanges = fogRanges)
            } else {
                null
            }

            coroutineContext.ensureActive()

            val renderData = renderOutput?.renderData
                ?: renderDataFactory.create(fogRanges = fogRanges)

            coroutineContext.ensureActive()
            val hiddenExploredRenderData = createHiddenExploredRenderData(
                buffer = buffer,
                exploredTiles = exploredTiles,
                visibilityTileMask = visibilityTileMask,
            )

            coroutineContext.ensureActive()

            PreparedFogBuffer(
                data = DisplayedFogData(
                    exploredTiles = exploredTiles,
                    fogRanges = fogRanges,
                    renderData = renderData,
                    hiddenExploredRenderData = hiddenExploredRenderData,
                    visibilityTileMask = visibilityTileMask,
                ),
            )
        }
    }

    private fun createHiddenExploredRenderData(
        buffer: FogBufferRegion,
        exploredTiles: Set<MapTile>,
        visibilityTileMask: Set<MapTile>,
    ): FogOfWarRenderData? {
        val hiddenExploredTiles = if (visibilityTileMask.isEmpty()) {
            exploredTiles
        } else {
            exploredTiles.filterTo(mutableSetOf()) { it !in visibilityTileMask }
        }
        val hiddenExploredRanges = fogOfWarCalculator.calculateExploredTileRanges(
            tileRange = buffer.bufferedTileRange,
            exploredTiles = hiddenExploredTiles,
        )

        return renderDataFactory.create(hiddenExploredRanges)
    }

    private fun refreshDisplayedVisibilityData() {
        val currentState = _state.value
        val displayedFogBuffer = currentState.displayedFogBuffer ?: return
        val displayedFogData = currentState.displayedFogData ?: return
        val visibilityTileMask = currentState.visibilityTileMask

        if (displayedFogData.visibilityTileMask == visibilityTileMask) {
            return
        }

        displayedVisibilityRefreshJob?.cancel()
        displayedVisibilityRefreshJob = scope.launch {
            try {
                val hiddenExploredRenderData = withContext(Dispatchers.Default) {
                    createHiddenExploredRenderData(
                        buffer = displayedFogBuffer,
                        exploredTiles = displayedFogData.exploredTiles,
                        visibilityTileMask = visibilityTileMask,
                    )
                }

                _state.update { current ->
                    val currentDisplayedFogData = current.displayedFogData

                    if (
                        current.displayedFogBuffer != displayedFogBuffer ||
                        currentDisplayedFogData == null ||
                        currentDisplayedFogData.exploredTiles != displayedFogData.exploredTiles ||
                        current.visibilityTileMask != visibilityTileMask
                    ) {
                        current
                    } else {
                        current.copy(
                            displayedFogData = currentDisplayedFogData.copy(
                                hiddenExploredRenderData = hiddenExploredRenderData,
                                visibilityTileMask = visibilityTileMask,
                            ),
                        )
                    }
                }
            } finally {
                displayedVisibilityRefreshJob = null

                val latestState = _state.value
                if (latestState.displayedFogData?.visibilityTileMask != latestState.visibilityTileMask) {
                    refreshDisplayedVisibilityData()
                }
            }
        }
    }

    private fun ensureDisplayedVisibilityDataUpToDate(buffer: FogBufferRegion) {
        val currentState = _state.value

        if (currentState.displayedFogBuffer != buffer) {
            return
        }

        val displayedFogData = currentState.displayedFogData ?: return
        if (displayedFogData.visibilityTileMask != currentState.visibilityTileMask) {
            refreshDisplayedVisibilityData()
        }
    }

    private fun buildUiState(state: State): FogOfWarUiState {
        val displayedFogData = state.displayedFogData
        val visibleExploredTileCount = state.visibleTileRange?.let { visibleRange ->
            displayedFogData?.exploredTiles?.count { tile ->
                visibleRange.contains(tile) && tile in state.visibilityTileMask
            }
        } ?: 0
        val fallbackFogTileRange = state.displayedFogBuffer
            ?.bufferedTileRange
            .takeUnless { state.isFogSuppressedByVisibleTileLimit }
        val fogRanges = displayedFogData?.fogRanges
            ?: fallbackFogTileRange?.let(::listOf)
            ?: emptyList()
        val activeRenderData = if (!state.isOverlayEnabled) {
            null
        } else {
            displayedFogData?.renderData
                ?: fallbackFogTileRange?.let(renderDataFactory::createFullRange)
        }

        return FogOfWarUiState(
            isOverlayEnabled = state.isOverlayEnabled,
            canonicalZoom = state.canonicalZoom,
            visibleBounds = state.visibleBounds,
            triggerBounds = state.displayedFogBuffer?.triggerBounds,
            bufferedBounds = state.displayedFogBuffer?.bufferedBounds,
            visibleTileRange = state.visibleTileRange,
            fogRanges = fogRanges,
            hiddenExploredRenderData = if (state.isOverlayEnabled) {
                displayedFogData?.hiddenExploredRenderData
            } else {
                null
            },
            activeRenderData = activeRenderData,
            handoffRenderData = if (state.isOverlayEnabled) {
                state.pendingHandoffRenderData
            } else {
                null
            },
            visibleTileCount = state.visibleTileCount,
            visibleExploredTileCount = visibleExploredTileCount,
            isSuppressedByVisibleTileLimit = state.isFogSuppressedByVisibleTileLimit,
            isRecomputing = state.isFogRecomputationInProgress,
        )
    }

    private fun buildVisibilityState(state: State): FogOfWarVisibilityState =
        FogOfWarVisibilityState(
            canonicalZoom = state.canonicalZoom,
            visibilityTileMask = state.visibilityTileMask,
        )

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
        val isSuppressedByVisibleTileLimit = viewport.visibleTileCount > MAX_VISIBLE_FOG_TILE_COUNT
        val isSuppressedByBufferedTileLimit = nextFogBuffer.bufferedTileRange.tileCount > MAX_BUFFERED_FOG_TILE_COUNT
        val isFogSuppressed = isSuppressedByVisibleTileLimit || isSuppressedByBufferedTileLimit

        _state.update {
            it.copy(
                visibleBounds = visibleBounds,
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

            !displayedFogBuffer.shouldRecompute(visibleBounds) -> {
                clearPendingFogRequest()
            }

            pendingFogBuffer?.shouldRecompute(visibleBounds) == false -> Unit

            else -> {
                enqueuePendingFogBuffer(buffer = nextFogBuffer, displayedFogBuffer = displayedFogBuffer)
            }
        }
    }

    private fun clearFogBufferState() {
        displayedFogObservationJob?.cancel()
        displayedFogObservationJob = null
        pendingFogProcessingJob?.cancel()
        pendingFogProcessingJob = null
        displayedVisibilityRefreshJob?.cancel()
        displayedVisibilityRefreshJob = null
        latestPendingFogBuffer = null

        _state.update {
            it.copy(
                displayedFogBuffer = null,
                pendingFogBuffer = null,
                displayedFogData = null,
                pendingHandoffRenderData = null,
                isFogRecomputationInProgress = false,
            )
        }
    }

    private fun clearPendingFogRequest() {
        latestPendingFogBuffer = null

        _state.update { current ->
            if (
                current.pendingFogBuffer == null &&
                current.pendingHandoffRenderData == null &&
                !current.isFogRecomputationInProgress
            ) {
                current
            } else {
                current.copy(
                    pendingFogBuffer = null,
                    pendingHandoffRenderData = null,
                    isFogRecomputationInProgress = false,
                )
            }
        }
    }

    private fun activateDisplayedFogBuffer(buffer: FogBufferRegion) {
        displayedFogObservationJob?.cancel()
        pendingFogProcessingJob?.cancel()
        pendingFogProcessingJob = null
        displayedVisibilityRefreshJob?.cancel()
        displayedVisibilityRefreshJob = null
        latestPendingFogBuffer = null

        _state.update {
            it.copy(
                displayedFogBuffer = buffer,
                pendingFogBuffer = null,
                displayedFogData = null,
                pendingHandoffRenderData = null,
                isFogRecomputationInProgress = false,
            )
        }

        startObservingDisplayedFogBuffer(buffer)
    }

    private fun enqueuePendingFogBuffer(
        buffer: FogBufferRegion,
        displayedFogBuffer: FogBufferRegion,
    ) {
        val handoffRenderData = buildPendingHandoffRenderData(
            displayedFogBuffer = displayedFogBuffer,
            pendingFogBuffer = buffer,
        )

        _state.update {
            it.copy(
                pendingFogBuffer = buffer,
                pendingHandoffRenderData = handoffRenderData,
                isFogRecomputationInProgress = true,
            )
        }

        latestPendingFogBuffer = buffer
        ensurePendingFogProcessorRunning()
    }

    private fun buildPendingHandoffRenderData(
        displayedFogBuffer: FogBufferRegion,
        pendingFogBuffer: FogBufferRegion,
    ): FogOfWarRenderData? = renderDataFactory.createFullRanges(
        pendingFogBuffer.bufferedTileRange.subtract(displayedFogBuffer.bufferedTileRange),
    )

    private fun ensurePendingFogProcessorRunning() {
        if (pendingFogProcessingJob?.isActive == true) {
            return
        }

        pendingFogProcessingJob = scope.launch {
            try {
                while (true) {
                    val buffer = latestPendingFogBuffer ?: break
                    latestPendingFogBuffer = null
                    val exploredTiles = getExploredTiles(buffer.bufferedTileRange)
                    val visibilityTileMask = _state.value.visibilityTileMask
                    val preparedFogBuffer = preparePendingFogBuffer(
                        buffer = buffer,
                        exploredTiles = exploredTiles,
                        visibilityTileMask = visibilityTileMask,
                    ) ?: continue

                    if (swapPreparedPendingFogBuffer(buffer, preparedFogBuffer)) {
                        ensureDisplayedVisibilityDataUpToDate(buffer)
                        startObservingDisplayedFogBuffer(
                            buffer = buffer,
                            seedExploredTiles = preparedFogBuffer.data.exploredTiles,
                        )

                        _state.value.visibleBounds?.takeIf(buffer::shouldRecompute)?.let(::updateFogViewport)
                    }
                }
            } finally {
                pendingFogProcessingJob = null

                if (latestPendingFogBuffer != null && _state.value.pendingFogBuffer != null) {
                    ensurePendingFogProcessorRunning()
                }
            }
        }
    }

    private suspend fun preparePendingFogBuffer(
        buffer: FogBufferRegion,
        exploredTiles: Set<MapTile>,
        visibilityTileMask: Set<MapTile>,
    ): PreparedFogBuffer? = try {
        prepareFogBufferData(
            buffer = buffer,
            exploredTiles = exploredTiles,
            visibilityTileMask = visibilityTileMask,
        )
    } catch (exception: CancellationException) {
        throw exception
    }

    private fun swapPreparedPendingFogBuffer(
        buffer: FogBufferRegion,
        preparedFogBuffer: PreparedFogBuffer,
    ): Boolean {
        var didSwap = false

        _state.update { current ->
            if (current.pendingFogBuffer != buffer || current.isFogSuppressedByVisibleTileLimit) {
                current
            } else {
                didSwap = true
                current.copy(
                    displayedFogBuffer = buffer,
                    pendingFogBuffer = null,
                    displayedFogData = preparedFogBuffer.data,
                    pendingHandoffRenderData = null,
                    isFogRecomputationInProgress = false,
                )
            }
        }

        return didSwap
    }

    private fun startObservingDisplayedFogBuffer(
        buffer: FogBufferRegion,
        seedExploredTiles: Set<MapTile>? = null,
    ) {
        displayedFogObservationJob?.cancel()
        displayedFogObservationJob = scope.launch {
            var shouldSkipSeed = seedExploredTiles != null

            observeFogExploredTiles(buffer.bufferedTileRange)
                .distinctUntilChanged()
                .collectLatest { exploredTiles ->
                    if (shouldSkipSeed && exploredTiles == seedExploredTiles) {
                        shouldSkipSeed = false
                        return@collectLatest
                    }

                    shouldSkipSeed = false
                    val visibilityTileMask = _state.value.visibilityTileMask
                    val preparedFogBuffer = try {
                        prepareFogBufferData(
                            buffer = buffer,
                            exploredTiles = exploredTiles,
                            visibilityTileMask = visibilityTileMask,
                        )
                    } catch (exception: CancellationException) {
                        throw exception
                    }

                    _state.update { current ->
                        if (current.displayedFogBuffer != buffer || current.isFogSuppressedByVisibleTileLimit) {
                            current
                        } else {
                            current.copy(displayedFogData = preparedFogBuffer.data)
                        }
                    }

                    ensureDisplayedVisibilityDataUpToDate(buffer)
                }
        }
    }

    @Immutable
    private data class State(
        val isOverlayEnabled: Boolean = true,
        val canonicalZoom: Int = CANONICAL_ZOOM,
        val visibilityTileMask: Set<MapTile> = emptySet(),
        val visibleBounds: GeoBounds? = null,
        val visibleTileRange: ExplorationTileRange? = null,
        val displayedFogBuffer: FogBufferRegion? = null,
        val pendingFogBuffer: FogBufferRegion? = null,
        val displayedFogData: DisplayedFogData? = null,
        val pendingHandoffRenderData: FogOfWarRenderData? = null,
        val visibleTileCount: Long = 0,
        val isFogSuppressedByVisibleTileLimit: Boolean = false,
        val isFogRecomputationInProgress: Boolean = false,
    )

    @Immutable
    private data class DisplayedFogData(
        val exploredTiles: Set<MapTile>,
        val fogRanges: List<ExplorationTileRange>,
        val renderData: FogOfWarRenderData?,
        val hiddenExploredRenderData: FogOfWarRenderData?,
        val visibilityTileMask: Set<MapTile>,
    )

    @Immutable
    private data class PreparedFogBuffer(
        val data: DisplayedFogData,
    )

    @Immutable
    private data class VisibilityStateSnapshot(
        val canonicalZoom: Int,
        val visibilityTileMask: Set<MapTile>,
    )

    private fun ExplorationTrackingSession.toVisibilityTileMask(
        canonicalZoom: Int,
        revealRadiusMeters: Double,
    ): Set<MapTile> {
        if (!isActive || status != ExplorationTrackingStatus.TRACKING) {
            return emptySet()
        }

        val location = lastKnownLocation ?: return emptySet()
        return revealTilesAround(
            point = location,
            radiusMeters = revealRadiusMeters,
            zoom = canonicalZoom,
        )
    }
}
