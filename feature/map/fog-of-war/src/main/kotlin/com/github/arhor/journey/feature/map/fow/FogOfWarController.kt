package com.github.arhor.journey.feature.map.fow

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTilePrototype
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.usecase.ObserveExplorationTileRuntimeConfigUseCase
import com.github.arhor.journey.domain.usecase.ObserveExploredTilesUseCase
import com.github.arhor.journey.feature.map.fow.model.FogBufferRegion
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderData
import com.github.arhor.journey.feature.map.fow.model.FogOfWarUiState
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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
    private val observeExploredTiles: ObserveExploredTilesUseCase,
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
    private var pendingFogPreparationJob: Job? = null

    val uiState: Flow<FogOfWarUiState> = combine(
        _state,
        observeExplorationTileRuntimeConfig(),
    ) { state, config -> state.copy(canonicalZoom = config.canonicalZoom) }
        .map(::buildUiState)
        .distinctUntilChanged()

//    fun setCanonicalZoom(canonicalZoom: Int) {
//        if (state.value.canonicalZoom == canonicalZoom) {
//            return
//        }
//
//        _state.update {
//            it.copy(canonicalZoom = canonicalZoom)
//        }
//
//        _state.value.visibleBounds?.let { visibleBounds ->
//            updateFogViewport(
//                visibleBounds = visibleBounds,
//                forceDisplayedBufferReplacement = true,
//            )
//        }
//    }

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
    ): Flow<Set<ExplorationTile>> = fogTileRange
        ?.let(observeExploredTiles::invoke)
        ?: flowOf(emptySet())

    private suspend fun prepareFogBufferData(
        buffer: FogBufferRegion,
        exploredTiles: Set<ExplorationTile>,
    ): PreparedFogBuffer {
        // Fog geometry is CPU-bound; keep it off the main dispatcher.
        return withContext(Dispatchers.Default) {
            val coroutineContext = currentCoroutineContext()
            val checkCancelled = { coroutineContext.ensureActive() }
            val diagnosticsEnabled = BuildConfig.DEBUG
            val fogRanges = fogOfWarCalculator.calculateUnexploredFogRanges(
                tileRange = buffer.bufferedTileRange,
                exploredTiles = exploredTiles,
                checkCancelled = checkCancelled,
            )
            val renderOutput = if (diagnosticsEnabled) {
                renderDataFactory.createDetailed(
                    fogRanges = fogRanges,
                    checkCancelled = checkCancelled,
                )
            } else {
                null
            }
            val renderData = renderOutput?.renderData ?: renderDataFactory.create(
                fogRanges = fogRanges,
                checkCancelled = checkCancelled,
            )

            PreparedFogBuffer(
                data = DisplayedFogData(
                    exploredTiles = exploredTiles,
                    fogRanges = fogRanges,
                    renderData = renderData,
                ),
            )
        }
    }

    private fun buildUiState(state: State): FogOfWarUiState {
        val displayedFogData = state.displayedFogData
        val exploredVisibleTileCount = state.visibleTileRange?.let { visibleRange ->
            displayedFogData?.exploredTiles?.count(visibleRange::contains)
        } ?: 0
        val fallbackFogTileRange = state.displayedFogBuffer
            ?.bufferedTileRange
            .takeUnless { state.isFogSuppressedByVisibleTileLimit }
        val fogRanges = displayedFogData?.fogRanges
            ?: fallbackFogTileRange?.let(::listOf)
            ?: emptyList()
        val renderData = displayedFogData?.renderData
            ?: fallbackFogTileRange
                ?.takeIf { state.isOverlayEnabled }
                ?.let(renderDataFactory::createFullRange)

        return FogOfWarUiState(
            isOverlayEnabled = state.isOverlayEnabled,
            canonicalZoom = state.canonicalZoom,
            visibleBounds = state.visibleBounds,
            triggerBounds = state.displayedFogBuffer?.triggerBounds,
            bufferedBounds = state.displayedFogBuffer?.bufferedBounds,
            visibleTileRange = state.visibleTileRange,
            fogRanges = fogRanges,
            renderData = renderData,
            visibleTileCount = state.visibleTileCount,
            exploredVisibleTileCount = exploredVisibleTileCount,
            isSuppressedByVisibleTileLimit = state.isFogSuppressedByVisibleTileLimit,
            isRecomputing = state.isFogRecomputationInProgress,
        )
    }

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

            !displayedFogBuffer.shouldRecompute(visibleBounds) -> Unit

            pendingFogBuffer?.shouldRecompute(visibleBounds) == false -> Unit

            else -> {
                preparePendingFogBuffer(nextFogBuffer)
            }
        }
    }

    private fun clearFogBufferState() {
        displayedFogObservationJob?.cancel()
        displayedFogObservationJob = null
        pendingFogPreparationJob?.cancel()
        pendingFogPreparationJob = null

        _state.update {
            it.copy(
                displayedFogBuffer = null,
                pendingFogBuffer = null,
                displayedFogData = null,
                isFogRecomputationInProgress = false,
            )
        }
    }

    private fun activateDisplayedFogBuffer(buffer: FogBufferRegion) {
        pendingFogPreparationJob?.cancel()
        pendingFogPreparationJob = null

        _state.update {
            it.copy(
                displayedFogBuffer = buffer,
                pendingFogBuffer = null,
                displayedFogData = null,
                isFogRecomputationInProgress = false,
            )
        }

        startObservingDisplayedFogBuffer(buffer)
    }

    private fun preparePendingFogBuffer(buffer: FogBufferRegion) {
        if (_state.value.pendingFogBuffer == buffer && pendingFogPreparationJob?.isActive == true) {
            return
        }

        pendingFogPreparationJob?.cancel()
        _state.update {
            it.copy(
                pendingFogBuffer = buffer,
                isFogRecomputationInProgress = true,
            )
        }

        pendingFogPreparationJob = scope.launch {
            val exploredTiles = observeFogExploredTiles(buffer.bufferedTileRange).first()
            val preparedFogBuffer = try {
                prepareFogBufferData(
                    buffer = buffer,
                    exploredTiles = exploredTiles,
                )
            } catch (exception: CancellationException) {
                recordFogPreparationCancellation()
                throw exception
            }
            var didSwap = false
            var shouldPrepareAnotherBuffer = false

            // Keep the old fog source active until the replacement render payload is fully ready.
            _state.update { current ->
                if (current.pendingFogBuffer != buffer || current.isFogSuppressedByVisibleTileLimit) {
                    current
                } else {
                    didSwap = true
                    val updatedState = current.copy(
                        displayedFogBuffer = buffer,
                        pendingFogBuffer = null,
                        displayedFogData = preparedFogBuffer.data,
                        isFogRecomputationInProgress = false,
                    )
                    shouldPrepareAnotherBuffer = updatedState.visibleBounds?.let(buffer::shouldRecompute) == true
                    updatedState
                }
            }

            if (!didSwap) {
                return@launch
            }

            pendingFogPreparationJob = null
            startObservingDisplayedFogBuffer(
                buffer = buffer,
                seedExploredTiles = preparedFogBuffer.data.exploredTiles,
            )

            if (shouldPrepareAnotherBuffer) {
                _state.value.visibleBounds?.let(::updateFogViewport)
            }
        }
    }

    private fun startObservingDisplayedFogBuffer(
        buffer: FogBufferRegion,
        seedExploredTiles: Set<ExplorationTile>? = null,
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
                    val preparedFogBuffer = try {
                        prepareFogBufferData(
                            buffer = buffer,
                            exploredTiles = exploredTiles,
                        )
                    } catch (exception: CancellationException) {
                        recordFogPreparationCancellation()
                        throw exception
                    }

                    _state.update { current ->
                        if (current.displayedFogBuffer != buffer || current.isFogSuppressedByVisibleTileLimit) {
                            current
                        } else {
                            current.copy(displayedFogData = preparedFogBuffer.data)
                        }
                    }
                }
        }
    }

    private fun recordFogPreparationCancellation() {
        _state.update { current ->
            current.copy(
                fogPreparationCancellationCount = current.fogPreparationCancellationCount + 1,
            )
        }
    }

    @Immutable
    private data class State(
        val isOverlayEnabled: Boolean = true,
        val canonicalZoom: Int = ExplorationTilePrototype.CANONICAL_ZOOM,
        val visibleBounds: GeoBounds? = null,
        val visibleTileRange: ExplorationTileRange? = null,
        val displayedFogBuffer: FogBufferRegion? = null,
        val pendingFogBuffer: FogBufferRegion? = null,
        val displayedFogData: DisplayedFogData? = null,
        val visibleTileCount: Long = 0,
        val isFogSuppressedByVisibleTileLimit: Boolean = false,
        val isFogRecomputationInProgress: Boolean = false,
        val fogPreparationCancellationCount: Long = 0,
    )

    @Immutable
    private data class DisplayedFogData(
        val exploredTiles: Set<ExplorationTile>,
        val fogRanges: List<ExplorationTileRange>,
        val renderData: FogOfWarRenderData?,
    )

    @Immutable
    private data class PreparedFogBuffer(
        val data: DisplayedFogData,
    )
}
