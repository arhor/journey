package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfigHolder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetExplorationTileCanonicalZoomUseCase @Inject constructor(
    private val configHolder: ExplorationTileRuntimeConfigHolder,
) {
    operator fun invoke(canonicalZoom: Int) {
        configHolder.setCanonicalZoom(canonicalZoom)
    }
}
