package com.github.arhor.journey.domain.model

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ExplorationTileRuntimeConfig(
    val canonicalZoom: Int = ExplorationTilePrototype.CANONICAL_ZOOM,
    val revealRadiusMeters: Double = ExplorationTilePrototype.REVEAL_RADIUS_METERS,
) {
    companion object {
        const val MIN_CANONICAL_ZOOM = 0
        const val MAX_CANONICAL_ZOOM = 30
        const val MIN_REVEAL_RADIUS_METERS = 1.0
    }
}

@Singleton
class ExplorationTileRuntimeConfigHolder @Inject constructor() {
    private val config = MutableStateFlow(ExplorationTileRuntimeConfig())

    fun observe(): StateFlow<ExplorationTileRuntimeConfig> = config.asStateFlow()

    fun snapshot(): ExplorationTileRuntimeConfig = config.value

    fun setCanonicalZoom(canonicalZoom: Int) {
        config.update {
            it.copy(
                canonicalZoom = canonicalZoom.coerceIn(
                    minimumValue = ExplorationTileRuntimeConfig.MIN_CANONICAL_ZOOM,
                    maximumValue = ExplorationTileRuntimeConfig.MAX_CANONICAL_ZOOM,
                ),
            )
        }
    }

    fun setRevealRadiusMeters(revealRadiusMeters: Double) {
        config.update {
            it.copy(
                revealRadiusMeters = revealRadiusMeters.coerceAtLeast(
                    minimumValue = ExplorationTileRuntimeConfig.MIN_REVEAL_RADIUS_METERS,
                ),
            )
        }
    }
}
