package com.github.arhor.journey.feature.map.fow

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.github.arhor.journey.core.common.DomainError
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.common.combine as combineOutputs
import com.github.arhor.journey.core.common.map
import com.github.arhor.journey.domain.CANONICAL_ZOOM
import com.github.arhor.journey.domain.internal.revealTilesAround
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.WatchtowerRevealSnapshot
import com.github.arhor.journey.domain.usecase.GetPackedExploredTilesUseCase
import com.github.arhor.journey.domain.usecase.ObserveClaimedWatchtowerRevealTilesUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTrackingSessionUseCase
import com.github.arhor.journey.domain.usecase.ObserveExplorationTileRuntimeConfigUseCase
import com.github.arhor.journey.domain.usecase.ObservePackedExploredTilesUseCase
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
    private val observePackedExploredTiles: ObservePackedExploredTilesUseCase,
    private val observeClaimedWatchtowerRevealTiles: ObserveClaimedWatchtowerRevealTilesUseCase,
    private val getPackedExploredTiles: GetPackedExploredTilesUseCase,
    private val renderDataFactory: FowRenderDataFactory,
    private val fogOfWarCalculator: FogOfWarCalculator,
    @Assisted private val scope: CoroutineScope,
) {
    @AssistedFactory
    fun interface Factory {
        fun create(scope: CoroutineScope): FogOfWarController
    }

    private val _state = MutableStateFlow(State())
    private val fogJobs = FogLifecycleJobs()
    private var queuedPendingSwapBuffer: FogBufferRegion? = null

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
                        currentState.visibility.liveTileMask != snapshot.liveVisibilityTileMask

                    _state.update { current ->
                        current.copy(
                            canonicalZoom = snapshot.canonicalZoom,
                            visibility = current.visibility.withLiveTileMask(snapshot.liveVisibilityTileMask),
                        )
                    }

                    when {
                        zoomChanged -> {
                            _state.value.viewport.visibleBounds?.let { visibleBounds ->
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
    ): Flow<Output<PackedTileSet, DomainError>> = fogTileRange
        ?.let { range ->
            observePackedExploredTiles(range).map { output ->
                output.map(PackedTileSet::fromPacked)
            }
        }
        ?: flowOf(Output.Success(PackedTileSet.Empty))

    private fun observePersistentWatchtowerReveal(
        buffer: FogBufferRegion,
        canonicalZoom: Int,
    ): Flow<Output<WatchtowerRevealSnapshot, DomainError>> = observeClaimedWatchtowerRevealTiles(
        bounds = buffer.bufferedBounds,
        canonicalZoom = canonicalZoom,
    )

    private suspend fun prepareFogBufferData(
        buffer: FogBufferRegion,
        exploredTiles: PackedTileSet,
        persistentRevealSnapshot: WatchtowerRevealSnapshot,
        liveVisibilityTileMask: PackedTileMask,
    ): DisplayedFogData {
        val persistentVisibilityTileMask = PackedTileMask.fromTiles(persistentRevealSnapshot.tiles)
        val clearedTiles = exploredTiles.union(persistentVisibilityTileMask.packedTiles)
        val visibilityTileMask = PackedTileMask.merge(liveVisibilityTileMask, persistentVisibilityTileMask)

        // Fog geometry is CPU-bound; keep it off the main dispatcher.
        return withContext(Dispatchers.Default) {
            val coroutineContext = currentCoroutineContext()
            val fogRanges = fogOfWarCalculator.calculateUnexploredFogRanges(
                tileRange = buffer.bufferedTileRange,
                exploredTileKeys = clearedTiles,
            )

            coroutineContext.ensureActive()

            val renderData = renderDataFactory.createDetailed(fogRanges = fogRanges)
                ?.renderData

            coroutineContext.ensureActive()
            val hiddenExploredRenderData = createHiddenExploredRenderData(
                buffer = buffer,
                clearedTiles = clearedTiles,
                visibilityTileMask = visibilityTileMask.packedTiles,
            )

            coroutineContext.ensureActive()

            DisplayedFogData(
                exploredTiles = exploredTiles,
                clearedTiles = clearedTiles,
                persistentVisibilityTileMask = persistentVisibilityTileMask,
                persistentVisibilityRevision = persistentRevealSnapshot.revision,
                fogRanges = fogRanges,
                renderData = renderData,
                hiddenExploredRenderData = hiddenExploredRenderData,
                visibilityTileMask = visibilityTileMask,
            )
        }
    }

    private fun createHiddenExploredRenderData(
        buffer: FogBufferRegion,
        clearedTiles: PackedTileSet,
        visibilityTileMask: PackedTileSet,
    ): FogOfWarRenderData? {
        val hiddenExploredTiles = if (visibilityTileMask.isEmpty()) {
            clearedTiles
        } else {
            clearedTiles.minus(visibilityTileMask)
        }
        val hiddenExploredRanges = fogOfWarCalculator.calculateExploredTileRanges(
            tileRange = buffer.bufferedTileRange,
            exploredTileKeys = hiddenExploredTiles,
        )

        return renderDataFactory.create(hiddenExploredRanges)
    }

    private fun refreshDisplayedVisibilityData() {
        val currentState = _state.value
        val displayedFog = currentState.fogBuffers.displayed ?: return
        val displayedFogBuffer = displayedFog.buffer
        val displayedFogData = displayedFog.data ?: return
        val visibilityTileMask = currentState.visibility.combinedTileMask

        if (displayedFogData.visibilityTileMask == visibilityTileMask) {
            return
        }

        fogJobs.cancelVisibilityRefresh()
        fogJobs.visibilityRefresh = scope.launch {
            try {
                val hiddenExploredRenderData = withContext(Dispatchers.Default) {
                    createHiddenExploredRenderData(
                        buffer = displayedFogBuffer,
                        clearedTiles = displayedFogData.clearedTiles,
                        visibilityTileMask = visibilityTileMask.packedTiles,
                    )
                }

                _state.update { current ->
                    val currentDisplayedFog = current.fogBuffers.displayed
                    val currentDisplayedFogData = currentDisplayedFog?.data
                    val displayedDataInputChanged = currentDisplayedFogData?.let { displayedData ->
                        displayedData.exploredTiles != displayedFogData.exploredTiles ||
                            displayedData.persistentVisibilityRevision !=
                            displayedFogData.persistentVisibilityRevision
                    } ?: true

                    if (
                        currentDisplayedFog?.buffer != displayedFogBuffer ||
                        displayedDataInputChanged ||
                        current.visibility.combinedTileMask != visibilityTileMask
                    ) {
                        current
                    } else {
                        current.copy(
                            fogBuffers = current.fogBuffers.withDisplayedData(
                                data = currentDisplayedFogData.copy(
                                    hiddenExploredRenderData = hiddenExploredRenderData,
                                    visibilityTileMask = visibilityTileMask,
                                ),
                            ),
                        )
                    }
                }
            } finally {
                fogJobs.visibilityRefresh = null

                val latestState = _state.value
                if (latestState.fogBuffers.displayed?.data?.visibilityTileMask !=
                    latestState.visibility.combinedTileMask
                ) {
                    refreshDisplayedVisibilityData()
                }
            }
        }
    }

    private fun ensureDisplayedVisibilityDataUpToDate(buffer: FogBufferRegion) {
        val currentState = _state.value

        val displayedFog = currentState.fogBuffers.displayed
        if (displayedFog?.buffer != buffer) {
            return
        }

        val displayedFogData = displayedFog.data ?: return
        if (displayedFogData.visibilityTileMask != currentState.visibility.combinedTileMask) {
            refreshDisplayedVisibilityData()
        }
    }

    private fun buildUiState(state: State): FogOfWarUiState {
        val displayedFog = state.fogBuffers.displayed
        val displayedFogData = displayedFog?.data
        val visibleExploredTileCount = state.viewport.visibleTileRange?.let { visibleRange ->
            displayedFogData?.clearedTiles?.countVisibleTiles(
                visibleRange = visibleRange,
                visibilityTileMask = state.visibility.combinedTileMask.packedTiles,
            )
        } ?: 0
        val fallbackFogTileRange = displayedFog
            ?.buffer
            ?.bufferedTileRange
            .takeUnless { state.suppression.isSuppressed }
        val fogRanges = displayedFogData?.fogRanges
            ?: fallbackFogTileRange?.let(::listOf)
            ?: emptyList()
        val activeRenderData = displayedFogData?.renderData
            ?: fallbackFogTileRange?.let(renderDataFactory::createFullRange)

        return FogOfWarUiState(
            canonicalZoom = state.canonicalZoom,
            visibleBounds = state.viewport.visibleBounds,
            triggerBounds = displayedFog?.buffer?.triggerBounds,
            bufferedBounds = displayedFog?.buffer?.bufferedBounds,
            visibleTileRange = state.viewport.visibleTileRange,
            fogRanges = fogRanges,
            hiddenExploredRenderData = displayedFogData?.hiddenExploredRenderData,
            activeRenderData = activeRenderData,
            handoffRenderData = state.fogBuffers.pendingSwap?.handoffRenderData,
            visibleTileCount = state.viewport.visibleTileCount,
            visibleExploredTileCount = visibleExploredTileCount,
            isSuppressed = state.suppression.isSuppressed,
            isRecomputing = state.fogBuffers.isPreparingSwap,
        )
    }

    private fun buildVisibilityState(state: State): FogOfWarVisibilityState =
        FogOfWarVisibilityState(
            canonicalZoom = state.canonicalZoom,
            visibilityTileMask = state.visibility.combinedTileMask.tiles,
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
        val suppression = FogSuppression.forTileCounts(
            visibleTileCount = viewport.visibleTileCount,
            bufferedTileCount = nextFogBuffer.bufferedTileRange.tileCount,
        )

        _state.update {
            it.copy(
                viewport = ViewportState(
                    visibleBounds = visibleBounds,
                    visibleTileRange = viewport.visibleTileRange,
                    visibleTileCount = viewport.visibleTileCount,
                ),
                suppression = suppression,
            )
        }

        if (suppression.isSuppressed) {
            clearFogBufferState()
            return
        }

        val updatedState = _state.value
        val displayedFogBuffer = updatedState.fogBuffers.displayed
            ?.buffer
            ?.takeIf { it.bufferedTileRange.zoom == updatedState.canonicalZoom }
        val pendingFogBuffer = updatedState.fogBuffers.pendingSwap
            ?.buffer
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
        fogJobs.cancelAll()
        queuedPendingSwapBuffer = null

        _state.update {
            it.copy(
                fogBuffers = FogBufferLifecycle(),
                visibility = it.visibility.withoutPersistentTileMask(),
            )
        }
    }

    private fun clearPendingFogRequest() {
        queuedPendingSwapBuffer = null

        _state.update { current ->
            if (!current.fogBuffers.isPreparingSwap) {
                current
            } else {
                current.copy(
                    fogBuffers = current.fogBuffers.withoutPendingSwap(),
                )
            }
        }
    }

    private fun activateDisplayedFogBuffer(buffer: FogBufferRegion) {
        fogJobs.cancelDisplayedObservation()
        fogJobs.cancelPendingSwapPreparation()
        fogJobs.cancelVisibilityRefresh()
        queuedPendingSwapBuffer = null

        _state.update {
            it.copy(
                fogBuffers = FogBufferLifecycle.displaying(buffer),
                visibility = it.visibility.withoutPersistentTileMask(),
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
                fogBuffers = it.fogBuffers.withPendingSwap(
                    buffer = buffer,
                    handoffRenderData = handoffRenderData,
                ),
            )
        }

        queuedPendingSwapBuffer = buffer
        ensurePendingFogProcessorRunning()
    }

    private fun buildPendingHandoffRenderData(
        displayedFogBuffer: FogBufferRegion,
        pendingFogBuffer: FogBufferRegion,
    ): FogOfWarRenderData? = renderDataFactory.createFullRanges(
        pendingFogBuffer.bufferedTileRange.subtract(displayedFogBuffer.bufferedTileRange),
    )

    private fun ensurePendingFogProcessorRunning() {
        if (fogJobs.pendingSwapPreparation?.isActive == true) {
            return
        }

        fogJobs.pendingSwapPreparation = scope.launch {
            var pausedAfterDependencyFailure = false
            try {
                while (true) {
                    val buffer = queuedPendingSwapBuffer ?: break
                    queuedPendingSwapBuffer = null
                    val exploredTiles = when (val result = getPackedExploredTiles(buffer.bufferedTileRange)) {
                        is Output.Success -> PackedTileSet.fromPacked(result.value)
                        is Output.Failure -> {
                            queuedPendingSwapBuffer = buffer
                            pausedAfterDependencyFailure = true
                            break
                        }
                    }
                    val displayedFogData = preparePendingFogBuffer(
                        buffer = buffer,
                        exploredTiles = exploredTiles,
                    ) ?: run {
                        queuedPendingSwapBuffer = buffer
                        pausedAfterDependencyFailure = true
                        break
                    }

                    if (swapPreparedPendingFogBuffer(buffer, displayedFogData)) {
                        ensureDisplayedVisibilityDataUpToDate(buffer)
                        startObservingDisplayedFogBuffer(
                            buffer = buffer,
                            seedExploredTiles = displayedFogData.exploredTiles,
                            seedPersistentRevealSnapshot = WatchtowerRevealSnapshot(
                                tiles = displayedFogData.persistentVisibilityTileMask.tiles,
                                revision = displayedFogData.persistentVisibilityRevision,
                            ),
                        )

                        _state.value.viewport.visibleBounds?.takeIf(buffer::shouldRecompute)?.let(::updateFogViewport)
                    }
                }
            } finally {
                fogJobs.pendingSwapPreparation = null

                if (!pausedAfterDependencyFailure &&
                    queuedPendingSwapBuffer != null &&
                    _state.value.fogBuffers.pendingSwap != null
                ) {
                    ensurePendingFogProcessorRunning()
                }
            }
        }
    }

    private suspend fun preparePendingFogBuffer(
        buffer: FogBufferRegion,
        exploredTiles: PackedTileSet,
    ): DisplayedFogData? = try {
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
            liveVisibilityTileMask = _state.value.visibility.liveTileMask,
        )
    } catch (exception: CancellationException) {
        throw exception
    }

    private fun swapPreparedPendingFogBuffer(
        buffer: FogBufferRegion,
        displayedFogData: DisplayedFogData,
    ): Boolean {
        var didSwap = false

        _state.update { current ->
            if (current.fogBuffers.pendingSwap?.buffer != buffer || current.suppression.isSuppressed) {
                current
            } else {
                didSwap = true
                current.copy(
                    fogBuffers = FogBufferLifecycle.displaying(
                        buffer = buffer,
                        data = displayedFogData,
                    ),
                    visibility = current.visibility.withPersistentTileMask(
                        tileMask = displayedFogData.persistentVisibilityTileMask,
                        revision = displayedFogData.persistentVisibilityRevision,
                    ),
                )
            }
        }

        return didSwap
    }

    private fun startObservingDisplayedFogBuffer(
        buffer: FogBufferRegion,
        seedExploredTiles: PackedTileSet? = null,
        seedPersistentRevealSnapshot: WatchtowerRevealSnapshot? = null,
    ) {
        fogJobs.cancelDisplayedObservation()
        fogJobs.displayedObservation = scope.launch {
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
                    val displayedFogData = try {
                        prepareFogBufferData(
                            buffer = buffer,
                            exploredTiles = snapshot.exploredTiles,
                            persistentRevealSnapshot = snapshot.persistentRevealSnapshot,
                            liveVisibilityTileMask = _state.value.visibility.liveTileMask,
                        )
                    } catch (exception: CancellationException) {
                        throw exception
                    }

                    _state.update { current ->
                        if (current.fogBuffers.displayed?.buffer != buffer || current.suppression.isSuppressed) {
                            current
                        } else {
                            current.copy(
                                fogBuffers = current.fogBuffers.withDisplayedData(displayedFogData),
                                visibility = current.visibility.withPersistentTileMask(
                                    tileMask = displayedFogData.persistentVisibilityTileMask,
                                    revision = displayedFogData.persistentVisibilityRevision,
                                ),
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
        val visibility: VisibilityMasks = VisibilityMasks(),
        val viewport: ViewportState = ViewportState(),
        val fogBuffers: FogBufferLifecycle = FogBufferLifecycle(),
        val suppression: FogSuppression = FogSuppression.None,
    )

    @Immutable
    private data class ViewportState(
        val visibleBounds: GeoBounds? = null,
        val visibleTileRange: ExplorationTileRange? = null,
        val visibleTileCount: Long = 0,
    )

    @Immutable
    private data class VisibilityMasks(
        val liveTileMask: PackedTileMask = PackedTileMask.Empty,
        val persistentTileMask: PackedTileMask = PackedTileMask.Empty,
        val combinedTileMask: PackedTileMask = PackedTileMask.Empty,
        val persistentRevision: Int = 0,
    ) {
        fun withLiveTileMask(tileMask: PackedTileMask): VisibilityMasks =
            copy(
                liveTileMask = tileMask,
                combinedTileMask = PackedTileMask.merge(
                    first = tileMask,
                    second = persistentTileMask,
                ),
            )

        fun withPersistentTileMask(
            tileMask: PackedTileMask,
            revision: Int,
        ): VisibilityMasks =
            copy(
                persistentTileMask = tileMask,
                combinedTileMask = PackedTileMask.merge(
                    first = liveTileMask,
                    second = tileMask,
                ),
                persistentRevision = revision,
            )

        fun withoutPersistentTileMask(): VisibilityMasks =
            withPersistentTileMask(
                tileMask = PackedTileMask.Empty,
                revision = 0,
            )
    }

    @Immutable
    private data class FogBufferLifecycle(
        val displayed: DisplayedFogBuffer? = null,
        val pendingSwap: PendingFogBufferSwap? = null,
    ) {
        val isPreparingSwap: Boolean get() = pendingSwap != null

        fun withPendingSwap(
            buffer: FogBufferRegion,
            handoffRenderData: FogOfWarRenderData?,
        ): FogBufferLifecycle =
            copy(
                pendingSwap = PendingFogBufferSwap(
                    buffer = buffer,
                    handoffRenderData = handoffRenderData,
                ),
            )

        fun withoutPendingSwap(): FogBufferLifecycle =
            if (pendingSwap == null) {
                this
            } else {
                copy(pendingSwap = null)
            }

        fun withDisplayedData(data: DisplayedFogData): FogBufferLifecycle {
            val displayed = displayed ?: return this
            return copy(displayed = displayed.copy(data = data))
        }

        companion object {
            fun displaying(
                buffer: FogBufferRegion,
                data: DisplayedFogData? = null,
            ): FogBufferLifecycle =
                FogBufferLifecycle(
                    displayed = DisplayedFogBuffer(
                        buffer = buffer,
                        data = data,
                    ),
                )
        }
    }

    @Immutable
    private data class DisplayedFogBuffer(
        val buffer: FogBufferRegion,
        val data: DisplayedFogData? = null,
    )

    @Immutable
    private data class PendingFogBufferSwap(
        val buffer: FogBufferRegion,
        val handoffRenderData: FogOfWarRenderData?,
    )

    private enum class FogSuppression {
        None,
        VisibleTileLimit,
        BufferedTileLimit,
        ;

        val isSuppressed: Boolean get() = this != None

        companion object {
            fun forTileCounts(
                visibleTileCount: Long,
                bufferedTileCount: Long,
            ): FogSuppression = when {
                visibleTileCount > MAX_VISIBLE_FOG_TILE_COUNT -> VisibleTileLimit
                bufferedTileCount > MAX_BUFFERED_FOG_TILE_COUNT -> BufferedTileLimit
                else -> None
            }
        }
    }

    private class FogLifecycleJobs {
        var displayedObservation: Job? = null
        var pendingSwapPreparation: Job? = null
        var visibilityRefresh: Job? = null

        fun cancelDisplayedObservation() {
            displayedObservation?.cancel()
            displayedObservation = null
        }

        fun cancelPendingSwapPreparation() {
            pendingSwapPreparation?.cancel()
            pendingSwapPreparation = null
        }

        fun cancelVisibilityRefresh() {
            visibilityRefresh?.cancel()
            visibilityRefresh = null
        }

        fun cancelAll() {
            cancelDisplayedObservation()
            cancelPendingSwapPreparation()
            cancelVisibilityRefresh()
        }
    }

    @Immutable
    private data class DisplayedFogData(
        val exploredTiles: PackedTileSet,
        val clearedTiles: PackedTileSet,
        val persistentVisibilityTileMask: PackedTileMask,
        val persistentVisibilityRevision: Int,
        val fogRanges: List<ExplorationTileRange>,
        val renderData: FogOfWarRenderData?,
        val hiddenExploredRenderData: FogOfWarRenderData?,
        val visibilityTileMask: PackedTileMask,
    )

    @Immutable
    private data class VisibilityStateSnapshot(
        val canonicalZoom: Int,
        val liveVisibilityTileMask: PackedTileMask,
    )

    @Immutable
    private data class ObservedFogBufferSnapshot(
        val exploredTiles: PackedTileSet,
        val persistentRevealSnapshot: WatchtowerRevealSnapshot,
    )

    private fun PackedTileSet.countVisibleTiles(
        visibleRange: ExplorationTileRange,
        visibilityTileMask: PackedTileSet,
    ): Int {
        if (visibilityTileMask.isEmpty()) {
            return 0
        }

        var count = 0
        for (index in 0 until size) {
            val key = keyAt(index)
            if (visibleRange.containsPackedTile(key) && key in visibilityTileMask) {
                count++
            }
        }

        return count
    }

    private fun ExplorationTrackingSession.toVisibilityTileMask(
        canonicalZoom: Int,
        revealRadiusMeters: Double,
    ): PackedTileMask {
        if (!isActive || status != ExplorationTrackingStatus.TRACKING) {
            return PackedTileMask.Empty
        }

        val location = lastKnownLocation ?: return PackedTileMask.Empty
        return PackedTileMask.fromTiles(
            revealTilesAround(
                point = location,
                radiusMeters = revealRadiusMeters,
                zoom = canonicalZoom,
            ),
        )
    }
}
