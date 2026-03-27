package com.github.arhor.journey.domain

import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExplorationTileRuntimeConfigHolder @Inject constructor() {
    private val config = MutableStateFlow(ExplorationTileRuntimeConfig())

    fun observe(): StateFlow<ExplorationTileRuntimeConfig> = config.asStateFlow()

    fun snapshot(): ExplorationTileRuntimeConfig = config.value

    fun setCanonicalZoom(canonicalZoom: Int) {
        config.update {
            it.copy(
                canonicalZoom = canonicalZoom.coerceIn(
                    minimumValue = ExplorationTileRuntimeConfig.Companion.MIN_CANONICAL_ZOOM,
                    maximumValue = ExplorationTileRuntimeConfig.Companion.MAX_CANONICAL_ZOOM,
                ),
            )
        }
    }

    fun setRevealRadiusMeters(revealRadiusMeters: Double) {
        config.update {
            it.copy(
                revealRadiusMeters = revealRadiusMeters.coerceAtLeast(
                    minimumValue = ExplorationTileRuntimeConfig.Companion.MIN_REVEAL_RADIUS_METERS,
                ),
            )
        }
    }
}
