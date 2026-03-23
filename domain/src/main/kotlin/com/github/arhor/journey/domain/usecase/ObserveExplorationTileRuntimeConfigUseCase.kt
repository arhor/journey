package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfig
import com.github.arhor.journey.domain.model.ExplorationTileRuntimeConfigHolder
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveExplorationTileRuntimeConfigUseCase @Inject constructor(
    private val configHolder: ExplorationTileRuntimeConfigHolder,
) {
    operator fun invoke(): StateFlow<ExplorationTileRuntimeConfig> = configHolder.observe()
}
