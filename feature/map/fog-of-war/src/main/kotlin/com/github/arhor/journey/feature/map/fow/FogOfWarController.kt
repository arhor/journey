package com.github.arhor.journey.feature.map.fow

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.github.arhor.journey.core.common.DomainError
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.common.combine as combineOutputs
import com.github.arhor.journey.domain.CANONICAL_ZOOM
import com.github.arhor.journey.domain.internal.revealTilesAround
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.WatchtowerRevealSnapshot
import com.github.arhor.journey.domain.usecase.GetExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.ObserveClaimedWatchtowerRevealTilesUseCase
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
import kotlinx.coroutines.flow.first
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
    private val observeClaimedWatchtowerRevealTiles: ObserveClaimedWatchtowerRevealTilesUseCase,
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
            ) { configOutput, sessionOutput ->
                combineOutputs(configOutput, sessionOutput) { config, session ->
                    VisibilityStateSnapshot(
                        canonicalZoom = config.canonicalZoom,
                        liveVisibilityTileMask = session.toVisibilityTileMask(
                            canonicalZoom = config.canonicalZoom,
                            revealRadiusMeters = config.revealRadiusMeters,
                        ),
                    )
                }
            }
                .distinctUntilChanged()
                .collectLatest { snapshotOutput ->
                    if (snapshotOutput !is Output.Success) {
                        return@collectLatest
                    }

                    val snapshot = snapshotOutput.value
                    val currentState = _state.value
                    val zoomChanged = currentState.canonicalZoom != snapshot.canonicalZoom
                    val visibilityMaskChanged =
                        currentState.liveVisibilityTileMask != snapshot.liveVisibilityTileMask

                    _state.update { current ->
                        current.copy(
                            canonicalZoom = snapshot.canonicalZoom,
                            liveVisibilityTileMask = snapshot.liveVisibilityTileMask,
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

    fun updateViewport(visibleBounds: GeoBounds) {
        updateFogViewport(visibleBounds = visibleBounds)
    }

    private fun observeFogExploredTiles(
        fogTileRange: ExplorationTileRange?,
    ): Flow<Output<Set<MapTile>, DomainError>> = fogTileRange
        ?.let(observeExploredTiles::invoke)
        ?: flowOf(Output.Success(emptySet()))

    private fun observePersistentWatchtowerReveal(
        buffer: FogBufferRegion,
        canonicalZoom: Int,
    ): Flow<Output<WatchtowerRevealSnapshot, DomainError>> = observeClaimedWatchtowerRevealTiles(
        bounds = buffer.bufferedBounds,
        canonicalZoom = canonicalZoom,
    )

    private suspend fun prepareFogBufferData(
        buffer: FogBufferRegion,
        exploredTiles: Set<MapTile>,
        persistentRevealSnapshot: WatchtowerRevealSnapshot,
        liveVisibilityTileMask: Set<MapTile>,
    ): PreparedFogBuffer {
        val persistentVisibilityTileMask = persistentRevealSnapshot.tiles
        val clearedTiles = mergeTileMasks(exploredTiles, persistentVisibilityTileMask)
        val visibilityTileMask = mergeTileMasks(liveVisibilityTileMask, persistentVisibilityTileMask)

        // Fog geometry is CPU-bound; keep it off the main dispatcher.
        return withContext(Dispatchers.Default) {
            val coroutineContext = currentCoroutineContext()
            val fogRanges = fogOfWarCalculator.calculateUnexploredFogRanges(
                tileRange = buffer.bufferedTileRange,
                exploredTiles = clearedTiles,
            )

            coroutineContext.ensureActive()

            val renderData = renderDataFactory.createDetailed(fogRanges = fogRanges)
                ?.renderData

            coroutineContext.ensureActive()
            val hiddenExploredRenderData = createHiddenExploredRenderData(
                buffer = buffer,
                clearedTiles = clearedTiles,
                visibilityTileMask = visibilityTileMask,
            )

            coroutineContext.ensureActive()

            PreparedFogBuffer(
                data = DisplayedFogData(
                    exploredTiles = exploredTiles,
                    clearedTiles = clearedTiles,
                    persistentVisibilityTileMask = persistentVisibilityTileMask,
                    persistentVisibilityRevision = persistentRevealSnapshot.revision,
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
        clearedTiles: Set<MapTile>,
        visibilityTileMask: Set<MapTile>,
    ): FogOfWarRenderData? {
        val hiddenExploredTiles = if (visibilityTileMask.isEmpty()) {
            clearedTiles
        } else {
            clearedTiles.filterTo(mutableSetOf()) { it !in visibilityTileMask }
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
        val visibilityTileMask = combinedVisibilityTileMask(currentState)

        if (displayedFogData.visibilityTileMask == visibilityTileMask) {
            return
        }

        displayedVisibilityRefreshJob?.cancel()
        displayedVisibilityRefreshJob = scope.launch {
            try {
                val hiddenExploredRenderData = withContext(Dispatchers.Default) {
                    createHiddenExploredRenderData(
                        buffer = displayedFogBuffer,
                        clearedTiles = displayedFogData.clearedTiles,
                        visibilityTileMask = visibilityTileMask,
                    )
                }

                _state.update { current ->
                    val currentDisplayedFogData = current.displayedFogData

                    if (
                        current.displayedFogBuffer != displayedFogBuffer ||
                        currentDisplayedFogData == null ||
                        currentDisplayedFogData.exploredTiles != displayedFogData.exploredTiles ||
                        currentDisplayedFogData.persistentVisibilityRevision != displayedFogData.persistentVisibilityRevision ||
                        combinedVisibilityTileMask(current) != visibilityTileMask
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
                if (latestState.displayedFogData?.visibilityTileMask != combinedVisibilityTileMask(latestState)) {
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
        if (displayedFogData.visibilityTileMask != combinedVisibilityTileMask(currentState)) {
            refreshDisplayedVisibilityData()
        }
    }

    private fun buildUiState(state: State): FogOfWarUiState {
        val displayedFogData = state.displayedFogData
        val visibilityTileMask = combinedVisibilityTileMask(state)
        val visibleExploredTileCount = state.visibleTileRange?.let { visibleRange ->
            displayedFogData?.clearedTiles?.count { tile ->
                visibleRange.contains(tile) && tile in visibilityTileMask
            }
        } ?: 0
        val fallbackFogTileRange = state.displayedFogBuffer
            ?.bufferedTileRange
            .takeUnless { state.isFogSuppressedByVisibleTileLimit }
        val fogRanges = displayedFogData?.fogRanges
            ?: fallbackFogTileRange?.let(::listOf)
            ?: emptyList()
        val activeRenderData = displayedFogData?.renderData
            ?: fallbackFogTileRange?.let(renderDataFactory::createFullRange)

        return FogOfWarUiState(
            canonicalZoom = state.canonicalZoom,
            visibleBounds = state.visibleBounds,
            triggerBounds = state.displayedFogBuffer?.triggerBounds,
            bufferedBounds = state.displayedFogBuffer?.bufferedBounds,
            visibleTileRange = state.visibleTileRange,
            fogRanges = fogRanges,
            hiddenExploredRenderData = displayedFogData?.hiddenExploredRenderData,
            activeRenderData = activeRenderData,
            handoffRenderData = state.pendingHandoffRenderData,
            visibleTileCount = state.visibleTileCount,
            visibleExploredTileCount = visibleExploredTileCount,
            isSuppressedByVisibleTileLimit = state.isFogSuppressedByVisibleTileLimit,
            isRecomputing = state.isFogRecomputationInProgress,
        )
    }

    private fun buildVisibilityState(state: State): FogOfWarVisibilityState =
        FogOfWarVisibilityState(
            canonicalZoom = state.canonicalZoom,
            visibilityTileMask = combinedVisibilityTileMask(state),
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
                persistentVisibilityTileMask = emptySet(),
                persistentVisibilityRevision = 0,
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
                persistentVisibilityTileMask = emptySet(),
                persistentVisibilityRevision = 0,
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
            var pausedAfterDependencyFailure = false
            try {
                while (true) {
                    val buffer = latestPendingFogBuffer ?: break
                    latestPendingFogBuffer = null
                    val exploredTiles = when (val result = getExploredTiles(buffer.bufferedTileRange)) {
                        is Output.Success -> result.value
                        is Output.Failure -> {
                            latestPendingFogBuffer = buffer
                            pausedAfterDependencyFailure = true
                            break
                        }
                    }
                    val preparedFogBuffer = preparePendingFogBuffer(
                        buffer = buffer,
                        exploredTiles = exploredTiles,
                    ) ?: run {
                        latestPendingFogBuffer = buffer
                        pausedAfterDependencyFailure = true
                        break
                    }

                    if (swapPreparedPendingFogBuffer(buffer, preparedFogBuffer)) {
                        ensureDisplayedVisibilityDataUpToDate(buffer)
                        startObservingDisplayedFogBuffer(
                            buffer = buffer,
                            seedExploredTiles = preparedFogBuffer.data.exploredTiles,
                            seedPersistentRevealSnapshot = WatchtowerRevealSnapshot(
                                tiles = preparedFogBuffer.data.persistentVisibilityTileMask,
                                revision = preparedFogBuffer.data.persistentVisibilityRevision,
                            ),
                        )

                        _state.value.visibleBounds?.takeIf(buffer::shouldRecompute)?.let(::updateFogViewport)
                    }
                }
            } finally {
                pendingFogProcessingJob = null

                if (!pausedAfterDependencyFailure &&
                    latestPendingFogBuffer != null &&
                    _state.value.pendingFogBuffer != null
                ) {
                    ensurePendingFogProcessorRunning()
                }
            }
        }
    }

    private suspend fun preparePendingFogBuffer(
        buffer: FogBufferRegion,
        exploredTiles: Set<MapTile>,
    ): PreparedFogBuffer? = try {
        val persistentRevealSnapshot = when (val result = observePersistentWatchtowerReveal(
            buffer = buffer,
            canonicalZoom = _state.value.canonicalZoom,
        ).first()) {
            is Output.Success -> result.value
            is Output.Failure -> return null
        }
        prepareFogBufferData(
            buffer = buffer,
            exploredTiles = exploredTiles,
            persistentRevealSnapshot = persistentRevealSnapshot,
            liveVisibilityTileMask = _state.value.liveVisibilityTileMask,
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
                    persistentVisibilityTileMask = preparedFogBuffer.data.persistentVisibilityTileMask,
                    persistentVisibilityRevision = preparedFogBuffer.data.persistentVisibilityRevision,
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
        seedPersistentRevealSnapshot: WatchtowerRevealSnapshot? = null,
    ) {
        displayedFogObservationJob?.cancel()
        displayedFogObservationJob = scope.launch {
            var shouldSkipSeed = seedExploredTiles != null && seedPersistentRevealSnapshot != null

            combine(
                observeFogExploredTiles(buffer.bufferedTileRange),
                observePersistentWatchtowerReveal(
                    buffer = buffer,
                    canonicalZoom = _state.value.canonicalZoom,
                ),
            ) { exploredTilesOutput, persistentRevealSnapshotOutput ->
                combineOutputs(exploredTilesOutput, persistentRevealSnapshotOutput) {
                        exploredTiles,
                        persistentRevealSnapshot,
                    ->
                    ObservedFogBufferSnapshot(
                        exploredTiles = exploredTiles,
                        persistentRevealSnapshot = persistentRevealSnapshot,
                    )
                }
            }
                .distinctUntilChanged()
                .collectLatest { snapshotOutput ->
                    if (snapshotOutput !is Output.Success) {
                        return@collectLatest
                    }

                    val snapshot = snapshotOutput.value
                    if (
                        shouldSkipSeed &&
                        snapshot.exploredTiles == seedExploredTiles &&
                        snapshot.persistentRevealSnapshot == seedPersistentRevealSnapshot
                    ) {
                        shouldSkipSeed = false
                        return@collectLatest
                    }

                    shouldSkipSeed = false
                    val preparedFogBuffer = try {
                        prepareFogBufferData(
                            buffer = buffer,
                            exploredTiles = snapshot.exploredTiles,
                            persistentRevealSnapshot = snapshot.persistentRevealSnapshot,
                            liveVisibilityTileMask = _state.value.liveVisibilityTileMask,
                        )
                    } catch (exception: CancellationException) {
                        throw exception
                    }

                    _state.update { current ->
                        if (current.displayedFogBuffer != buffer || current.isFogSuppressedByVisibleTileLimit) {
                            current
                        } else {
                            current.copy(
                                displayedFogData = preparedFogBuffer.data,
                                persistentVisibilityTileMask = preparedFogBuffer.data.persistentVisibilityTileMask,
                                persistentVisibilityRevision = preparedFogBuffer.data.persistentVisibilityRevision,
                            )
                        }
                    }

                    ensureDisplayedVisibilityDataUpToDate(buffer)
                }
        }
    }

    @Immutable
    private data class State(
        val canonicalZoom: Int = CANONICAL_ZOOM,
        val liveVisibilityTileMask: Set<MapTile> = emptySet(),
        val persistentVisibilityTileMask: Set<MapTile> = emptySet(),
        val persistentVisibilityRevision: Int = 0,
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
        val clearedTiles: Set<MapTile>,
        val persistentVisibilityTileMask: Set<MapTile>,
        val persistentVisibilityRevision: Int,
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
        val liveVisibilityTileMask: Set<MapTile>,
    )

    @Immutable
    private data class ObservedFogBufferSnapshot(
        val exploredTiles: Set<MapTile>,
        val persistentRevealSnapshot: WatchtowerRevealSnapshot,
    )

    private fun combinedVisibilityTileMask(state: State): Set<MapTile> = mergeTileMasks(
        first = state.liveVisibilityTileMask,
        second = state.persistentVisibilityTileMask,
    )

    private fun mergeTileMasks(
        first: Set<MapTile>,
        second: Set<MapTile>,
    ): Set<MapTile> = when {
        first.isEmpty() -> second
        second.isEmpty() -> first
        else -> buildSet(first.size + second.size) {
            addAll(first)
            addAll(second)
        }
    }

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
